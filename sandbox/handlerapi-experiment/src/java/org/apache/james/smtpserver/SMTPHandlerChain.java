/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/



package org.apache.james.smtpserver;

import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.james.smtpserver.core.CoreCmdHandlerLoader;
import org.apache.james.smtpserver.core.filter.CoreFilterCmdHandlerLoader;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
  * The SMTPHandlerChain is per service object providing access
  * ConnectHandlers, Commandhandlers and message handlers
  */
public class SMTPHandlerChain extends AbstractLogEnabled implements Configurable, Serviceable, Initializable {

    private List handlers = new LinkedList();

    private ServiceManager serviceManager;
    
    /**
     * @see org.apache.avalon.framework.service.Serviceable#service(org.apache.avalon.framework.service.ServiceManager)
     */
    public void service(ServiceManager arg0) throws ServiceException {
        serviceManager = arg0;
    }

    /**
     * ExtensibleHandler wiring
     * @throws WiringException 
     */
    private void wireExtensibleHandlers() throws WiringException {
        for (Iterator h = handlers.iterator(); h.hasNext(); ) {
            Object handler = h.next();
            if (handler instanceof ExtensibleHandler) {
                List markerInterfaces = ((ExtensibleHandler) handler).getMarkerInterfaces();
                for (int i= 0;i < markerInterfaces.size(); i++) {
                    Class markerInterface = (Class) markerInterfaces.get(i);
                    List extensions = getHandlers(markerInterface);
                    ((ExtensibleHandler) handler).wireExtensions(markerInterface,extensions);
                }
            }
        }
    }

    /**
     * loads the various handlers from the configuration
     * 
     * @param configuration
     *            configuration under handlerchain node
     */
    public void configure(Configuration configuration)
            throws ConfigurationException {
        if (configuration == null
                || configuration.getChildren("handler") == null
                || configuration.getChildren("handler").length == 0) {
            configuration = new DefaultConfiguration("handlerchain");
            Properties cmds = new Properties();
            cmds.setProperty("Default CoreCmdFilterHandlerLoader",
                    CoreFilterCmdHandlerLoader.class.getName());
            cmds.setProperty("Default CoreCmdHandlerLoader", CoreCmdHandlerLoader.class
                    .getName());
            Enumeration e = cmds.keys();
            while (e.hasMoreElements()) {
                String cmdName = (String) e.nextElement();
                String className = cmds.getProperty(cmdName);
                ((DefaultConfiguration) configuration).addChild(addHandler(
                        cmdName, className));
            }
        }
        if (configuration != null) {
            Configuration[] children = configuration.getChildren("handler");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            // load the BaseFilterCmdHandler
            loadClass(classLoader, CoreFilterCmdHandlerLoader.class.getName(),
                    addHandler(null, CoreFilterCmdHandlerLoader.class.getName()));

            // load the configured handlers
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    String className = children[i].getAttribute("class");
                    if (className != null) {

                        // ignore base handlers.
                        if (!className.equals(CoreFilterCmdHandlerLoader.class
                                .getName())
                                && !className.equals(CoreCmdHandlerLoader.class
                                        .getName())) {

                            // load the handler
                            loadClass(classLoader, className, children[i]);
                        }
                    }
                }

                // load the BaseCmdHandler and SendMailHandler
                loadClass(classLoader, CoreCmdHandlerLoader.class.getName(),
                        addHandler(null, CoreCmdHandlerLoader.class.getName()));
            }
        }
    }
    
    /**
     * @see org.apache.avalon.framework.activity.Initializable#initialize()
     */
    public void initialize() throws Exception {
        SMTPCommandDispatcherLineHandler commandDispatcherLineHandler = new SMTPCommandDispatcherLineHandler();
        commandDispatcherLineHandler.enableLogging(getLogger());
        handlers.add(commandDispatcherLineHandler);
        
        Iterator h = handlers.iterator();
    
        while(h.hasNext()) {
            Object next = h.next();
            ContainerUtil.initialize(next);
        }
        wireExtensibleHandlers();

    }

    /**
     * Load and add the classes to the handler map
     * 
     * @param classLoader The classLoader to use
     * @param className The class name 
     * @param config The configuration 
     * @throws ConfigurationException Get thrown on error
     */
    private void loadClass(ClassLoader classLoader, String className,
            Configuration config) throws ConfigurationException {
        try {
            Object handler = classLoader.loadClass(className).newInstance();

            // enable logging
            ContainerUtil.enableLogging(handler, getLogger());

            // servicing the handler
            ContainerUtil.service(handler, serviceManager);

            // configure the handler
            ContainerUtil.configure(handler, config);

            // if it is a commands handler add it to the map with key as command
            // name
            if (handler instanceof CommandsHandler) {
                Map c = ((CommandsHandler) handler).getCommands();

                Iterator cmdKeys = c.keySet().iterator();

                while (cmdKeys.hasNext()) {
                    String commandName = cmdKeys.next().toString();
                    String cName = c.get(commandName).toString();

                    DefaultConfiguration cmdConf = new DefaultConfiguration(
                            "handler");
                    cmdConf.setAttribute("command", commandName);
                    cmdConf.setAttribute("class", cName);

                    loadClass(classLoader, cName, cmdConf);
                }

            }

            if (getLogger().isInfoEnabled()) {
                getLogger().info("Added Handler: " + className);
            }
            
            // fill the big handler table
            handlers.add(handler);
            
        } catch (ClassNotFoundException ex) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error("Failed to add Commandhandler: " + className,
                        ex);
            }
            throw new ConfigurationException("Failed to add Commandhandler: "
                    + className, ex);
        } catch (IllegalAccessException ex) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error("Failed to add Commandhandler: " + className,
                        ex);
            }
            throw new ConfigurationException("Failed to add Commandhandler: "
                    + className, ex);
        } catch (InstantiationException ex) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error("Failed to add Commandhandler: " + className,
                        ex);
            }
            throw new ConfigurationException("Failed to add Commandhandler: "
                    + className, ex);
        } catch (ServiceException e) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error(
                        "Failed to service Commandhandler: " + className, e);
            }
            throw new ConfigurationException("Failed to add Commandhandler: "
                    + className, e);
        }
    }

    /**
     * Return a DefaultConfiguration build on the given command name and classname
     * 
     * @param cmdName The command name
     * @param className The class name
     * @return DefaultConfiguration
     */
    private DefaultConfiguration addHandler(String cmdName, String className) {
        DefaultConfiguration cmdConf = new DefaultConfiguration("handler");
        cmdConf.setAttribute("command",cmdName);
        cmdConf.setAttribute("class",className);
        return cmdConf;
    }
    
    /**
     * Returns a list of handler of the requested type.
     * 
     * @param type the type of handler we're interested in
     * @return a List of handlers
     */
    public LinkedList getHandlers(Class type) {
        LinkedList result = new LinkedList();
        for (Iterator i = handlers.iterator(); i.hasNext(); ) {
            Object handler = i.next();
            if (type.isInstance(handler)) {
                result.add(handler);
            }
        }
        return result;
    }

}
