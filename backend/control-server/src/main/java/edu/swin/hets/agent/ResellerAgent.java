package edu.swin.hets.agent;

import edu.swin.hets.helper.GoodMessageTemplates;
import edu.swin.hets.helper.IMessageHandler;
import edu.swin.hets.helper.PowerSaleAgreement;
import edu.swin.hets.helper.PowerSaleProposal;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.io.IOException;
import java.util.Vector;

/******************************************************************************
 *  Use: A simple example of a reseller agent class that is not dependant
 *       on any events, should be extended later for more detailed classes.
 *  Notes:
 *       To buy: this->CFP :: PROP->this :: this->ACC || this->REJ
 *       When selling: CFP->this :: this->PROP :: ACC->this || REJ->this
 *  Services Registered: "reseller"
 *  Messages Understood:
 *       - CFP : Used to negotiate purchasing electricity from reseller.
 *             content Object: A PowerSaleProposal object
 *       - ACCEPT_PROPOSAL : Used to signify a proposal has been accepted by
 *                           someone buying from us.
 *             content Object: A PowerSaleAgreement object
 *       - REJECT_PROPOSAL : Used to signify failed proposal from someone we
 *                           wanted to sell to.
 *             content Object: A PowerSaleProposal object
 *       - PROPOSAL : Used when someone wants to propose selling or buying
 *                    electricity from us.
 *             content Object: A PowerSaleProposal object
 *  Messages sent:
 *       - CFP : Used to negotiate purchasing electricity from power plant agent
 *             content Object: A PowerSaleProposal object
 *       - ACCEPT_PROPOSAL : Used to signify a proposal has been accepted.
 *             content Object: A PowerSaleAgreement object
 *       - REJECT_PROPOSAL : Used to signify failed proposal
 *             content Object: A PowerSaleProposal object
 *       - PROPOSAL : Used to send a proposal back to a home user.
 *             content Object: A PowerSaleProposal object
 *****************************************************************************/
public class ResellerAgent extends BaseAgent {
    private double _current_sell_price;
    private double _current_by_price;
    private double _min_purchase_amount;
    private double _next_purchased_amount;
    private double _next_required_amount;
    private Vector<Double> _future_needs;
    private Vector<PowerSaleAgreement> _current_buy_agrements;
    private Vector<PowerSaleAgreement> _current_sell_agrements;
    private Vector<PowerSaleProposal> _awaitingProposals;

    private MessageTemplate CFPMessageTemplate = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.CFP),
            GoodMessageTemplates.ContatinsString("edu.swin.hets.helper.PowerSaleProposal"));
    private MessageTemplate PropAcceptedMessageTemplate = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
            GoodMessageTemplates.ContatinsString("edu.swin.hets.helper.PowerSaleAgreement"));
    private MessageTemplate PropRejectedMessageTemplate = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
            GoodMessageTemplates.ContatinsString("edu.swin.hets.helper.PowerSaleProposal"));
    private MessageTemplate PropMessageTemplate = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
            GoodMessageTemplates.ContatinsString("edu.swin.hets.helper.PowerSaleProposal"));

    protected void setup() {
        super.setup();
        _current_by_price = 10;
        _current_sell_price = 1.0;
        _min_purchase_amount = 100;
        _next_required_amount = 200; //TODO let home users set demand.
        _awaitingProposals = new Vector<PowerSaleProposal>();
        _current_buy_agrements = new Vector<PowerSaleAgreement>();
        _current_sell_agrements = new Vector<PowerSaleAgreement>();
        addMessageHandler(PropAcceptedMessageTemplate, new ProposalAcceptedHandler());
        addMessageHandler(PropRejectedMessageTemplate, new ProposalRejectedHandler());
        addMessageHandler(CFPMessageTemplate, new CFPHandler());
        addMessageHandler(PropMessageTemplate, new ProposalHandler());
        RegisterAMSService(getAID().getName(), "reseller");
    }

    protected String getJSON() {
        return "Not implemented";
    }

    protected void TimeExpired() {
        _next_purchased_amount = 0;
        Vector<PowerSaleAgreement> toRemove = new Vector<>();
        for (PowerSaleAgreement agreement : _current_buy_agrements) {
            if (agreement.getEndTime() < _current_globals.getTime()) {
                // No longer valid
                toRemove.add(agreement);
            }
        }
        _current_buy_agrements.removeAll(toRemove);
        for (PowerSaleAgreement agreement : _current_buy_agrements) {
            // We have purchased this electricty.
            _next_purchased_amount += agreement.getAmount();
        }
        _next_required_amount = 0;
        for (PowerSaleAgreement agreement: _current_sell_agrements) {
            if (agreement.getEndTime() >= _current_globals.getTime()) {
                toRemove.add(agreement);// No longer valid
            }
        }
        _current_sell_agrements.removeAll(toRemove);
        for (PowerSaleAgreement agreement: _current_sell_agrements) {
            _next_required_amount += agreement.getAmount();
        }
        // We now know how much we have bought and how much we need to buy
        // Start making CFP's to get electricity we need.
        if (_next_required_amount > _next_purchased_amount) {
            sendBuyCFP();
        }
    }

    protected void TimePush(int ms_left) {
        if (_next_required_amount - _next_purchased_amount > 0.1) {
            LogVerbose(getName() + "requires: " + _next_required_amount + " purchased: " + _next_purchased_amount);
            sendBuyCFP(); // We need to buy more electricity
        }
        // We have enough electricity do nothing.
    }

    // Wanting to buy electricity
    private void sendBuyCFP() {
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        DFAgentDescription[] powerplants = getService("powerplant");
        for (DFAgentDescription powerplant : powerplants) {
            cfp.addReceiver(powerplant.getName()); //CFP to each power plant
        }
        //TODO make more complicated logic.
        PowerSaleProposal prop = new PowerSaleProposal(
                _next_required_amount - _next_purchased_amount,4);
        prop.setBuyerAID(getAID());
        try {
            cfp.setContentObject(prop);
        } catch (IOException e) {
            LogError("Could not attach a proposal to a message, exception thrown");
        }
        send(cfp);
    }

    // Someone is offering to sell us electricity
    private class ProposalHandler implements IMessageHandler {
        public void Handler(ACLMessage msg) {
            PowerSaleProposal proposed = getPowerSalePorposal(msg);
            if (proposed.getCost() <= _current_by_price ) {
                // Accept
                LogVerbose(getName() + " agreed to buy " + proposed.getAmount() + " electricity for " +
                        proposed.getDuration() + " time slots from " + proposed.getSellerAID().getName());
                PowerSaleAgreement contract = new PowerSaleAgreement(proposed, _current_globals.getTime());
                _current_buy_agrements.add(contract);
                _next_purchased_amount += contract.getAmount();
                ACLMessage acceptMsg = msg.createReply();
                acceptMsg.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                addPowerSaleAgreement(acceptMsg, contract);
                send(acceptMsg);
            } else {
                // To expensive
                sendRejectProposalMessage(msg);
            }
        }
    }

    // Someone is buying electricity off us
    private class ProposalAcceptedHandler implements IMessageHandler {
        public void Handler(ACLMessage msg) {
            PowerSaleAgreement agreement = getPowerSaleAgrement(msg);
            _current_sell_agrements.add(agreement);
        }
    }

    // Someone is not buying electricity off us
    private class ProposalRejectedHandler implements IMessageHandler {
        public void Handler(ACLMessage msg) {
            //TODO, maybe make counter offer.
        }
    }

    // Someone is wanting to buy electricity off us
    private class CFPHandler implements IMessageHandler {
        public void Handler(ACLMessage msg) {
            // A request for a price on electricity
            PowerSaleProposal proposed = getPowerSalePorposal(msg);
            if (_next_required_amount > _next_purchased_amount) {
                //TODO buy more electricity and send back a quote
            }
            else {
                //Make offer of electricity to home
                if (proposed.getCost() > 0 && proposed.getCost() < _current_sell_price) {
                    return; //TODO, re negotiate for better price.
                }
                if (proposed.getCost() < 0) {
                    proposed.setCost(_current_sell_price);
                } // If neither of the above price set is more than we charge.
                proposed.setSellerAID(getAID());
                ACLMessage response = msg.createReply();
                response.setPerformative(ACLMessage.PROPOSE);
                addPowerSaleProposal(response, proposed);
                send(response);
                LogVerbose(getName() + " sending a proposal to " + msg.getSender().getName());
            }
        }
    }

    private void quoteNoLongerValid(ACLMessage msg) {

    }
}
