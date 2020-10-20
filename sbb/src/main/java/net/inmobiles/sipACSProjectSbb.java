package net.inmobiles;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.*;
import javax.slee.facilities.TimerFacility;

import org.apache.log4j.Logger;
import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;
import org.mobicents.slee.*;

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestedAction;
import jain.protocol.ip.mgcp.message.parms.RequestedEvent;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.MgcpEndpointActivity;
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

	// MGCP
	private JainMgcpProvider mgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpAcif;
	public static final String ENDPOINT_NAME = "mobicents/ivr/$";
	public static final String JBOSS_BIND_ADDRESS = "192.168.153.168";
	public static final int MGCP_PEER_PORT = 2427;
	public static final int MGCP_PORT = 2727;
	public static final String TONE_WELCOME = "http://192.168.153.174:8080/restcomm/audio/ringing.wav";

	// UTIL
	private TimerFacility timerFacility;

	public void onServiceStartedEvent(javax.slee.serviceactivity.ServiceStartedEvent event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		log.info(" ***** Service Started  ***** ");
	}

	public void onINVITE(javax.sip.RequestEvent event, ActivityContextInterface aci/* , EventContext eventContext */) {

		log.info(" ***** sipACSProjectSbb Received onINVITE-REQ  ***** ");

		replyToRequestEvent(event, Response.TRYING);
		Request request = event.getRequest();
		FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
		ContactHeader contactHeader = (ContactHeader) event.getRequest().getHeader(ContactHeader.NAME);

		// this.setAPartyContact(contactHeader);
		log.info(" FromHeader:" + from + ", ToHeader:" + to + ", ContactHeader:" + contactHeader);

		try {

			// ServerTransaction st = event.getServerTransaction();
			// if (st == null) {
			// log.info("ServerTransaction is NULL !!! we need to CREATE A NEW [ST]");
			// st = sipProvider.getNewServerTransaction(request);
			// log.info("ServerTransaction NEW ONE CREATED !!! [ST] == "+ st.toString());
			// } else {
			// log.info("ServerTransaction is NOT NULL !!! [ST] == "+ st.toString());
			// }
			// dialog = st.getDialog();
			// st.sendResponse(response);

			log.info("*** Get SDP call ***");
			String sdp = new String(event.getRequest().getRawContent());
			// this.setInitialSdp(sdp);
			
			createNewMgcpConnection(sdp);

		} catch (Exception e) {
			log.error("An error has occured on onINVITE:", e);
		}

	}

	public void createNewMgcpConnection(String sdp) {

		log.info(" ***** sipACSProjectSbb Start Creating a NEW MGCP CONNECTION  ***** ");
		CallIdentifier callID = mgcpProvider.getUniqueCallIdentifier();
		EndpointIdentifier endpointID = new EndpointIdentifier(ENDPOINT_NAME,
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
		CreateConnection createConnection = new CreateConnection(this, callID, endpointID, ConnectionMode.SendRecv);

		try {
			createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
			log.info("MGCP CONNECTION SETTLE!");
			int txID = mgcpProvider.getUniqueTransactionHandler();
			createConnection.setTransactionHandle(txID);
			MgcpConnectionActivity connectionActivity = null;
			connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
			ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(connectionActivity);
			epnAci.attach(sbbContext.getSbbLocalObject());
			mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
			log.info(" *** FIRE MGCP CREATE CONNECTION *** ");
		} catch (ConflictingParameterException e) {
			// should never happen
			log.error("MGCP CONNECTION NOT SETTLE!");
		}

	}

	public void onCREATE_CONNECTION_RESPONSE(jain.protocol.ip.mgcp.message.CreateConnectionResponse event,
			ActivityContextInterface aci/* , EventContext eventContext */) {

		log.info(" ***** sipACSProjectSbb onCREATE_CONNECTION_RESPONSE  ***** ");
		ServerTransaction txn = getServerTransactionModed();
		Request request = txn.getRequest();
		ReturnCode status = event.getReturnCode();
		ToHeader toHead = (ToHeader) request.getHeader(ToHeader.NAME);
		log.info("the Status code is: " + status);

		switch (status.getValue()) {

		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
			log.info("onCREATE_CONNECTION_RESPONSE was executed normally: " + status.getValue());
			this.setEndpointName(event.getSpecificEndpointIdentifier().getLocalEndpointName());
			ConnectionIdentifier connectionIdentifier = event.getConnectionIdentifier();
			this.setConnectionIdentifier(connectionIdentifier.toString());
			String sdp = event.getLocalConnectionDescriptor().toString();
			sdp = sdp.trim();
			// log.info("The SDP inside onCREATE_CONNCETION_RESPONSE: " + sdp);

			try {
				// Sending RQNT
				ContentTypeHeader contentType = headerFactory.createContentTypeHeader("application", "sdp");
				Address contactAddress = toHead.getAddress();
				ContactHeader contact = headerFactory.createContactHeader(contactAddress);
				log.info("TONE_URI inCreateConnectionResponseEvent IS : " + TONE_WELCOME);
				sendRQNT(TONE_WELCOME, true);

				// Sending Response
				Response response = messageFactory.createResponse(Response.SESSION_PROGRESS, request, contentType,
						sdp.getBytes());
				response.addHeader(headerFactory.createAllowHeader(
						"INVITE,ACK,OPTIONS,BYE,CANCEL,SUBSCRIBE,NOTIFY,REFER,MESSAGE,INFO,PING,PRACK,UPDATE"));
				response.setHeader(contact);

				try {
					txn.sendResponse(response);
					log.info("The [183 response] was Sent on onCREATE_CONNECTION_RESPONSE");
				} catch (SipException e) {
					// TODO Auto-generated catch block
					log.error("An error has occured on SipException while trying to send OK Response", e);
				} catch (InvalidArgumentException e) {
					// TODO Auto-generated catch block
					log.error("An error has occured on InvalidArgumentException while trying to send OK Response", e);
				}

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				log.error("Exception occured on Creating ContentTypeHeader: ", e);
			}

			break;
		default:
			try {
				log.info("onCREATE_CONNECTION_RESPONSE was not executed normally: " + status.getValue());
			} catch (Exception ex) {
				log.error("Exception occured on onCREATE_CONNECTION_RESPONSE: ", ex);
			}
		}

	}

	private void sendRQNT(String mediaPath, boolean createActivity) {
		log.info(" ***** sipACSProjectSbb onSendRQNT  ***** ");
		log.info("JBOSS_BIND_ADDRESS inSendRQNT IS : " + JBOSS_BIND_ADDRESS);
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(),
				JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);
		NotificationRequest notificationRequest = new NotificationRequest(this, endpointID,
				mgcpProvider.getUniqueRequestIdentifier());
		EventName[] signalRequests = { new EventName(AUPackage.AU, AUMgcpEvent.aupc
				.withParm("ip=" + mediaPath + " mx=" + 1 + " mn=" + 1 + " fdt=" + 50 + " idt=" + 50 + " ni=false")) };

		notificationRequest.setSignalRequests(signalRequests);
		RequestedAction[] actions = new RequestedAction[] { RequestedAction.NotifyImmediately };

		RequestedEvent[] requestedEvent = {
				new RequestedEvent(new EventName(AUPackage.AU, AUMgcpEvent.auoc/* , connectionIdentifier */), actions),
				new RequestedEvent(new EventName(AUPackage.AU, AUMgcpEvent.auof/* , connectionIdentifier */),
						actions), };
		
		notificationRequest.setRequestedEvents(requestedEvent);
		notificationRequest.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		NotifiedEntity notifiedEntity = new NotifiedEntity(JBOSS_BIND_ADDRESS, JBOSS_BIND_ADDRESS, MGCP_PORT);
		notificationRequest.setNotifiedEntity(notifiedEntity);
		
		if (createActivity) {
			MgcpEndpointActivity endpointActivity = null;
			try {
				endpointActivity = mgcpProvider.getEndpointActivity(endpointID);
				ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(endpointActivity);
				epnAci.attach(sbbContext.getSbbLocalObject());
			} catch (FactoryException ex) {
				ex.printStackTrace();
			} catch (NullPointerException ex) {
				ex.printStackTrace();
			} catch (UnrecognizedActivityException ex) {
				ex.printStackTrace();
			}
		}
		
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });
		log.info("RQNT-----Request--------Sent");
	}

	public void onNOTIFICATION_REQUEST_RESPONSE(jain.protocol.ip.mgcp.message.NotificationRequestResponse event, ActivityContextInterface aci/*, EventContext eventContext*/) {
		log.info(" ***** sipACSProjectSbb onNOTIFICATION_REQUEST_RESPONSE  ***** ");
		ReturnCode status = event.getReturnCode();
		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
			log.info("The Announcement should have been started");
			break;
		default:
			ReturnCode rc = event.getReturnCode();
			log.info("The Announcement has failed");
			log.info("RQNT failed. Value = " + rc.getValue() + " Comment = " + rc.getComment());
			// TODO : Send DLCX to MMS. Send BYE to UA
			// endMgcp(aci);
			
			//releaseState();
			
			break;
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

	private ServerTransaction getServerTransactionModed() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for (ActivityContextInterface activity : activities) {
			if (activity.getActivity() instanceof ServerTransaction) {
				return (ServerTransaction) activity.getActivity();
			}
		}
		return null;
	}

	private ClientTransaction getClientTransactionModed() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for (ActivityContextInterface activity : activities) {
			if (activity.getActivity() instanceof ClientTransaction) {
				return (ClientTransaction) activity.getActivity();
			}
		}
		return null;
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

			// MGCP
			mgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider/demo");
			mgcpAcif = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory/demo");

			// UTILS

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

	// 'endpointName' CMP field setter
	public abstract void setEndpointName(String value);

	// 'endpointName' CMP field getter
	public abstract String getEndpointName();

	// 'connectionIdentifier' CMP field setter
	public abstract void setConnectionIdentifier(String value);

	// 'connectionIdentifier' CMP field getter
	public abstract String getConnectionIdentifier();

	
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
