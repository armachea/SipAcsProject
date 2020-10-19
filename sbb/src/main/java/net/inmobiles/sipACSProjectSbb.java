package net.inmobiles;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.*;
import javax.slee.facilities.TimerFacility;

import org.apache.log4j.Logger;
import org.mobicents.slee.*;

import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

public abstract class sipACSProjectSbb implements Sbb, sipACSProject {

	// LOGGER
	static final Logger log = Logger.getLogger(sipACSProjectSbb.class);
	// SIP
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipProvider;
	private Dialog dialog;
	
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;

	// UTIL
	private TimerFacility timerFacility;

	public void onServiceStartedEvent(javax.slee.serviceactivity.ServiceStartedEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		log.info(" ***** Service Started  ***** ");
	}

	public void onINVITE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

		log.info(" ***** sipACSProjectSbb Received onINVITE-REQ  ***** ");

		// send "trying" response
		replyToRequestEvent(event, Response.RINGING);
		Request request = event.getRequest();

		FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
		ContactHeader contactHeader = (ContactHeader) event.getRequest().getHeader(ContactHeader.NAME);
		log.info(" FromHeader:" + from + ", ToHeader:" + to + ", ContactHeader:" + contactHeader);

		try {
			
			ServerTransaction st = event.getServerTransaction();
			if (st == null) {
				log.info("Server Transaction is NULL !!! we need to CREATE A NEW [ST]");
                st = sipProvider.getNewServerTransaction(request);
            }
			
		//	dialog = st.getDialog();
		//	st.sendResponse(response);

			
		} catch (Exception e) {
			log.error("An error has occured on onINVITE:", e);
		}
		
	}

	public void onCANCEL(net.java.slee.resource.sip.CancelRequestEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

	}

	public void onBYE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

	}

	public void onOPTIONS(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

	}

	public void onREGISTER(javax.sip.RequestEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		
		Request request = event.getRequest();
		ServerTransaction serverTx = event.getServerTransaction();
		try {
			// from Profile
			log.info(" sipACSProjectSbb Registration START");
			ContactHeader contactHeader = (ContactHeader) event.getRequest().getHeader(ContactHeader.NAME);
			log.info("the contact is: " + contactHeader);
			Response response = messageFactory.createResponse(Response.OK, request);
			response.setHeader(contactHeader);
			serverTx.sendResponse(response);
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
		log.info("sipACSProjectSbb Registration 200 OK SUCCESS");
	}

	public void onTRYING(javax.sip.ResponseEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

	}

	private void replyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction()
					.sendResponse(sipProvider.getMessageFactory().createResponse(status, event.getRequest()));
		} catch (Throwable e) {
			log.error(" !!! Failed to reply to request event:\n" + event, e);
		}
	}

	public void setSbbContext(SbbContext context) {

		this.sbbContext = (SbbContextExt) context;
		try {
			log.info(" sipACSProjectSbb context and Link Factory: STARTED ! ");
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx
					.lookup("slee/resources/jainsip/1.2/acifactory");
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");

			// SIP
			 addressFactory = sipProvider.getAddressFactory();
			 headerFactory = sipProvider.getHeaderFactory();
			 messageFactory = sipProvider.getMessageFactory();

			// this.timerFacility = (TimerFacility) ctx.lookup("slee/facilities/timer");

		} catch (Exception e) {
			// TODO: handle exception
			log.error("Could not set sipACSProjectSbb context and Link Factory: ", e);
		}

	}

	protected SbbContextExt getSbbContext() {
		return sbbContext;
	}

	private SbbContextExt sbbContext; // This SBB's SbbContext

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
	}

	public void sbbPostCreate() throws javax.slee.CreateException {
	}

	public void sbbActivate() {
	}

	public void sbbPassivate() {
	}

	public void sbbRemove() {
	}

	public void sbbLoad() {
	}

	public void sbbStore() {
	}

	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {
	}

	public void sbbRolledBack(RolledBackContext context) {
	}

	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this
	 * method, the sbbContext variable and the variable assignment in
	 * setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */

}
