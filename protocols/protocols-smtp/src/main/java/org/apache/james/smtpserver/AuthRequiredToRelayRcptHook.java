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

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.smtp.core.AbstractAuthRequiredToRelayRcptHook;

public class AuthRequiredToRelayRcptHook extends AbstractAuthRequiredToRelayRcptHook {

	private DomainList domains;

	@Inject
	public void setDomainList(@Named("domainlist") DomainList domains) {
		this.domains = domains;
	}

	/**
	 * @see org.apache.james.protocols.smtp.core.AbstractAuthRequiredToRelayRcptHook#isLocalDomain(java.lang.String)
	 */
	protected boolean isLocalDomain(String domain) {
		try {
			System.out.println("Pesquisando domain para " + domain);
			System.out.println("================");
			System.out.println("Domains disponiveis: ");
			for (String d : domains.getDomains()) {
				System.out.println(d);
				if (domain.endsWith(d)) {
					System.out.println("Domain [" + domain + "] é local com domainlist [" + d + "]");
					return true;
				}
			}
			// return domains.containsDomain(domain);
			return false;
		} catch (DomainListException e) {
			return false;
		}
	}
}
