<?xml version="1.0" encoding="UTF-8"?>
<!-- ! Licensed to the Apache Software Foundation (ASF) under one ! ! or 
	more contributor license agreements. See the NOTICE file ! ! distributed 
	with this work for additional information ! ! regarding copyright ownership. 
	The ASF licenses this file ! ! to you under the Apache License, Version 2.0 
	(the ! ! "License"); you may not use this file except in compliance ! ! with 
	the License. You may obtain a copy of the License at ! ! ! ! http://www.apache.org/licenses/LICENSE-2.0 
	! ! ! ! Unless required by applicable law or agreed to in writing, ! ! software 
	distributed under the License is distributed on an ! ! "AS IS" BASIS, WITHOUT 
	WARRANTIES OR CONDITIONS OF ANY ! ! KIND, either express or implied. See 
	the License for the ! ! specific language governing permissions and limitations 
	! ! under the License. ! -->


<beans xmlns="http://www.springframework.org/schema/beans"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:rabbit="http://www.springframework.org/schema/rabbit"
	xsi:schemaLocation="http://www.springframework.org/schema/beans
         http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
         http://www.springframework.org/schema/rabbit
         http://www.springframework.org/schema/rabbit/spring-rabbit.xsd">

	<bean id="mailqueuefactory" class="org.apache.james.queue.activemq.AmqpMailQueueFactory" />

	<rabbit:connection-factory id="connectionFactory" />

	<rabbit:admin connection-factory="connectionFactory" />

	<bean id="jmsConnectionFactory"
		class="org.springframework.amqp.rabbit.connection.CachingConnectionFactory">
	</bean>
</beans>
