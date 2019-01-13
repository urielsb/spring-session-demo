/**
 * 
 */
package com.uriel.session.demo.listener;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * @author Uriel Santoyo
 *
 */
public class ExpiredSessionListener implements ApplicationListener<SessionExpiredEvent> {
	
	private static Logger log = Logger.getLogger(ExpiredSessionListener.class);

	@Override
	public void onApplicationEvent(SessionExpiredEvent event) {
		log.trace("onApplicationEvent() -->");
		log.info("Expired session ID: " + event.getSession().getId());
		log.trace("onApplicationEvent() <--");
	}

}
