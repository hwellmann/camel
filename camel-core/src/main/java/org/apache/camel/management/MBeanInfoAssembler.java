/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.management.Descriptor;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedNotification;
import org.apache.camel.api.management.ManagedNotifications;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel specific {@link javax.management.MBeanInfo} assembler that reads the
 * details from the {@link ManagedResource}, {@link ManagedAttribute}, {@link ManagedOperation},
 * {@link ManagedNotification}, and {@link ManagedNotifications} annotations.
 */
public class MBeanInfoAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(MBeanInfoAssembler.class);

    /**
     * Gets the {@link ModelMBeanInfo} for the given managed bean
     *
     * @param defaultManagedBean  the default managed bean
     * @param customManagedBean   an optional custom managed bean
     * @param objectName   the object name
     * @return the model info
     * @throws JMException is thrown if error creating the model info
     */
    public ModelMBeanInfo getMBeanInfo(Object defaultManagedBean, Object customManagedBean, String objectName) throws JMException {
        // maps and lists to contain information about attributes and operations
        Map<String, ManagedAttributeInfo> attributes = new HashMap<String, ManagedAttributeInfo>();
        Set<ManagedOperationInfo> operations = new LinkedHashSet<ManagedOperationInfo>();
        Set<ModelMBeanAttributeInfo> mBeanAttributes = new LinkedHashSet<ModelMBeanAttributeInfo>();
        Set<ModelMBeanOperationInfo> mBeanOperations = new LinkedHashSet<ModelMBeanOperationInfo>();
        Set<ModelMBeanNotificationInfo> mBeanNotifications = new LinkedHashSet<ModelMBeanNotificationInfo>();

        // extract details from default managed bean
        extractAttributesAndOperations(defaultManagedBean.getClass(), attributes, operations);
        extractMbeanAttributes(defaultManagedBean, attributes, mBeanAttributes, mBeanOperations);
        extractMbeanOperations(defaultManagedBean, operations, mBeanOperations);
        extractMbeanNotifications(defaultManagedBean, mBeanNotifications);

        // extract details from custom managed bean
        if (customManagedBean != null) {
            extractAttributesAndOperations(customManagedBean.getClass(), attributes, operations);
            extractMbeanAttributes(customManagedBean, attributes, mBeanAttributes, mBeanOperations);
            extractMbeanOperations(customManagedBean, operations, mBeanOperations);
            extractMbeanNotifications(customManagedBean, mBeanNotifications);
        }

        // create the ModelMBeanInfo
        String name = getName(customManagedBean != null ? customManagedBean : defaultManagedBean, objectName);
        String description = getDescription(customManagedBean != null ? customManagedBean : defaultManagedBean, objectName);
        ModelMBeanAttributeInfo[] arrayAttributes = mBeanAttributes.toArray(new ModelMBeanAttributeInfo[mBeanAttributes.size()]);
        ModelMBeanOperationInfo[] arrayOperations = mBeanOperations.toArray(new ModelMBeanOperationInfo[mBeanOperations.size()]);
        ModelMBeanNotificationInfo[] arrayNotifications = mBeanNotifications.toArray(new ModelMBeanNotificationInfo[mBeanNotifications.size()]);

        ModelMBeanInfo info = new ModelMBeanInfoSupport(name, description, arrayAttributes, null, arrayOperations, arrayNotifications);
        LOG.trace("Created ModelMBeanInfo {}", info);
        return info;
    }

    private void extractAttributesAndOperations(Class<?> managedClass, Map<String, ManagedAttributeInfo> attributes, Set<ManagedOperationInfo> operations) {
        // extract the class
        doExtractAttributesAndOperations(managedClass, attributes, operations);

        // and then any sub classes
        if (managedClass.getSuperclass() != null) {
            Class<?> clazz = managedClass.getSuperclass();
            // skip any JDK classes
            if (!clazz.getName().startsWith("java")) {
                LOG.trace("Extracting attributes and operations from sub class: {}", clazz);
                extractAttributesAndOperations(clazz, attributes, operations);
            }
        }

        // and then any additional interfaces (as interfaces can be annotated as well)
        if (managedClass.getInterfaces() != null) {
            for (Class<?> clazz : managedClass.getInterfaces()) {
                // recursive as there may be multiple interfaces
                if (clazz.getName().startsWith("java")) {
                    // skip any JDK classes
                    continue;
                }
                LOG.trace("Extracting attributes and operations from implemented interface: {}", clazz);
                extractAttributesAndOperations(clazz, attributes, operations);
            }
        }
    }

    private void doExtractAttributesAndOperations(Class<?> managedClass, Map<String, ManagedAttributeInfo> attributes, Set<ManagedOperationInfo> operations) {
        LOG.trace("Extracting attributes and operations from class: {}", managedClass);
        for (Method method : managedClass.getDeclaredMethods()) {
            LOG.trace("Extracting attributes and operations from method: {}", method);

            ManagedAttribute ma = method.getAnnotation(ManagedAttribute.class);
            if (ma != null) {
                String key;
                String desc = ma.description();
                Method getter = null;
                Method setter = null;

                if (IntrospectionSupport.isGetter(method)) {
                    key = IntrospectionSupport.getGetterShorthandName(method);
                    getter = method;
                } else if (IntrospectionSupport.isSetter(method)) {
                    key = IntrospectionSupport.getSetterShorthandName(method);
                    setter = method;
                } else {
                    throw new IllegalArgumentException("@ManagedAttribute can only be used on Java bean methods, was: " + method + " on bean: " + managedClass);
                }

                // they key must be capitalized
                key = ObjectHelper.capitalize(key);

                // lookup first
                ManagedAttributeInfo info = attributes.get(key);
                if (info == null) {
                    info = new ManagedAttributeInfo(key, desc);
                }
                if (getter != null) {
                    info.setGetter(getter);
                }
                if (setter != null) {
                    info.setSetter(setter);
                }

                attributes.put(key, info);
            }

            // operations
            ManagedOperation mo = method.getAnnotation(ManagedOperation.class);
            if (mo != null) {
                String desc = mo.description();
                Method operation = method;
                operations.add(new ManagedOperationInfo(desc, operation));
            }
        }
    }

    private void extractMbeanAttributes(Object managedBean, Map<String, ManagedAttributeInfo> attributes,
                                        Set<ModelMBeanAttributeInfo> mBeanAttributes, Set<ModelMBeanOperationInfo> mBeanOperations) throws IntrospectionException {

        for (ManagedAttributeInfo info : attributes.values()) {
            ModelMBeanAttributeInfo mbeanAttribute = new ModelMBeanAttributeInfo(info.getKey(), info.getDescription(), info.getGetter(), info.getSetter());

            // add missing attribute descriptors, this is needed to have attributes accessible
            Descriptor desc = mbeanAttribute.getDescriptor();
            if (info.getGetter() != null) {
                desc.setField("getMethod", info.getGetter().getName());
                // attribute must also be added as mbean operation
                ModelMBeanOperationInfo mbeanOperation = new ModelMBeanOperationInfo(info.getKey(), info.getGetter());
                mBeanOperations.add(mbeanOperation);
            }
            if (info.getSetter() != null) {
                desc.setField("setMethod", info.getSetter().getName());
                // attribute must also be added as mbean operation
                ModelMBeanOperationInfo mbeanOperation = new ModelMBeanOperationInfo(info.getKey(), info.getSetter());
                mBeanOperations.add(mbeanOperation);
            }
            mbeanAttribute.setDescriptor(desc);

            mBeanAttributes.add(mbeanAttribute);
            LOG.trace("Assembled attribute: {}", mbeanAttribute);
        }
    }

    private void extractMbeanOperations(Object managedBean, Set<ManagedOperationInfo> operations, Set<ModelMBeanOperationInfo> mBeanOperations) {
        for (ManagedOperationInfo info : operations) {
            ModelMBeanOperationInfo mbean = new ModelMBeanOperationInfo(info.getDescription(), info.getOperation());
            mBeanOperations.add(mbean);
            LOG.trace("Assembled operation: {}", mbean);
        }
    }

    private void extractMbeanNotifications(Object managedBean, Set<ModelMBeanNotificationInfo> mBeanNotifications) {
        ManagedNotifications notifications = managedBean.getClass().getAnnotation(ManagedNotifications.class);
        if (notifications != null) {
            for (ManagedNotification notification : notifications.value()) {
                ModelMBeanNotificationInfo info = new ModelMBeanNotificationInfo(notification.notificationTypes(), notification.name(), notification.description());
                mBeanNotifications.add(info);
                LOG.trace("Assembled notification: {}", info);
            }
        }
    }

    private String getDescription(Object managedBean, String objectName) {
        ManagedResource mr = ObjectHelper.getAnnotation(managedBean, ManagedResource.class);
        return mr != null ? mr.description() : "";
    }

    private String getName(Object managedBean, String objectName) {
        return managedBean.getClass().getName();
    }

    private static final class ManagedAttributeInfo {
        private String key;
        private String description;
        private Method getter;
        private Method setter;

        private ManagedAttributeInfo(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }

        public Method getGetter() {
            return getter;
        }

        public void setGetter(Method getter) {
            this.getter = getter;
        }

        public Method getSetter() {
            return setter;
        }

        public void setSetter(Method setter) {
            this.setter = setter;
        }

        @Override
        public String toString() {
            return "ManagedAttributeInfo: [" + key + " + getter: " + getter + ", setter: " + setter + "]";
        }
    }

    private static final class ManagedOperationInfo {
        private final String description;
        private final Method operation;

        private ManagedOperationInfo(String description, Method operation) {
            this.description = description;
            this.operation = operation;
        }

        public String getDescription() {
            return description;
        }

        public Method getOperation() {
            return operation;
        }

        @Override
        public String toString() {
            return "ManagedOperationInfo: [" + operation + "]";
        }
    }

}
