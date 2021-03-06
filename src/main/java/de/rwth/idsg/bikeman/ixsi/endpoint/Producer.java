package de.rwth.idsg.bikeman.ixsi.endpoint;

import de.rwth.idsg.bikeman.ixsi.CommunicationContext;
import xjc.schema.ixsi.IxsiMessageType;

import java.util.Set;

/**
 * Created by max on 08/09/14.
 */
public interface Producer {

    /**
     * To be used in request/response communication
     */
    void send(CommunicationContext context);

    /**
     * To be used for push messages
     */
    void send(IxsiMessageType ixsi, Set<String> systemIdSet);
}
