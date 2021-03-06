/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.Task;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandLineOptionReader {

    private ListMultimap<Class, StaticCommandLineOptionDescriptor> cachedStaticDescriptors = ArrayListMultimap.create();

    public List<CommandLineOptionDescriptor> getCommandLineOptions(Task task) {
        final Class<? extends Task> taskClazz = task.getClass();
        Map<String, CommandLineOptionDescriptor> options = new HashMap<String, CommandLineOptionDescriptor>();

        if (!cachedStaticDescriptors.containsKey(task.getClass())) {
            //task class not processed yet
            for (Class<?> type = taskClazz; type != Object.class && type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!Modifier.isStatic(method.getModifiers())) {
                        CommandLineOption commandLineOption = method.getAnnotation(CommandLineOption.class);
                        if (commandLineOption != null) {
                            StaticCommandLineOptionDescriptor staticCommandLineOptionDescriptor = new StaticCommandLineOptionDescriptor(commandLineOption, method);
                            assertMethodTypeSupported(staticCommandLineOptionDescriptor, taskClazz, method);

                            CommandLineOptionDescriptor optionDescriptor = new InstanceCommandLineOptionDescriptor(task, staticCommandLineOptionDescriptor);
                            if (options.containsKey(optionDescriptor.getName())) {
                                throw new CommandLineArgumentException(String.format("Option '%s' linked to multiple methods in class '%s'.",
                                        optionDescriptor.getName(), taskClazz.getName()));
                            }
                            cachedStaticDescriptors.put(taskClazz, staticCommandLineOptionDescriptor);
                            options.put(optionDescriptor.getName(), optionDescriptor);
                        }
                    }
                }
            }
        }else{
            for (StaticCommandLineOptionDescriptor staticCommandLineOptionDescriptor : cachedStaticDescriptors.get(taskClazz)) {
                options.put(staticCommandLineOptionDescriptor.getName(), new InstanceCommandLineOptionDescriptor(task, staticCommandLineOptionDescriptor));
            }
        }
        return CollectionUtils.sort(options.values());
    }

    private void assertMethodTypeSupported(CommandLineOptionDescriptor optionDescriptor, Class taskClazz, Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new CommandLineArgumentException(String.format("Option '%s' cannot be linked to methods with multiple parameters in class '%s#%s'.",
                    optionDescriptor.getName(), taskClazz.getName(), method.getName()));
        }

        if (parameterTypes.length == 1) {
            final Class<?> parameterType = parameterTypes[0];
            if (!(parameterType == Boolean.class || parameterType == Boolean.TYPE)
                    && !parameterType.isAssignableFrom(String.class)
                    && !parameterType.isEnum()) {
                throw new CommandLineArgumentException(String.format("Option '%s' cannot be casted to parameter type '%s' in class '%s'.",
                        optionDescriptor.getName(), parameterType.getName(), taskClazz.getName()));
            }
        }
    }
}
