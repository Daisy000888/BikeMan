package de.rwth.idsg.bikeman.web.rest;

import com.google.common.base.Optional;
import de.rwth.idsg.bikeman.ixsi.store.WebSocketSessionStore;
import de.rwth.idsg.bikeman.ixsi.store.AvailabilityStore;
import de.rwth.idsg.bikeman.ixsi.store.BookingAlertStore;
import de.rwth.idsg.bikeman.ixsi.store.ConsumptionStore;
import de.rwth.idsg.bikeman.ixsi.store.ExternalBookingStore;
import de.rwth.idsg.bikeman.ixsi.store.PlaceAvailabilityStore;
import de.rwth.idsg.bikeman.web.rest.dto.monitor.EndpointDTO;
import de.rwth.idsg.bikeman.web.rest.dto.monitor.StoreDTO;
import de.rwth.idsg.bikeman.web.rest.dto.monitor.StoreItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import xjc.schema.ixsi.BookingTargetIDType;
import xjc.schema.ixsi.UserInfoType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by max on 15/07/15.
 */
@RestController
@RequestMapping(value = "api/", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class MonitorResource {

    @Autowired private WebSocketSessionStore webSocketSessionStore;
    @Autowired private AvailabilityStore availabilityStore;
    @Autowired private ConsumptionStore consumptionStore;
    @Autowired private ExternalBookingStore externalBookingStore;
    @Autowired private PlaceAvailabilityStore placeAvailabilityStore;
    @Autowired private BookingAlertStore bookingAlertStore;

    private static final String BASE_PATH                   = "monitor/";
    private static final String IXSI_SESSION_STATUS         = "monitor/session-status";
    private static final String IXSI_SESSION_KILL           = "monitor/session/{systemId}/{sessionId}";
    private static final String IXSI_STORE_LIST             = "monitor/store-list";
    private static final String IXSI_STORE_AVAIL            = "monitor/store/avail";
    private static final String IXSI_STORE_PLACE_AVAIL      = "monitor/store/place-avail";
    private static final String IXSI_STORE_CONSUMPTION      = "monitor/store/consumption";
    private static final String IXSI_STORE_EX_BOOK          = "monitor/store/external-book";
    private static final String IXSI_STORE_BOOKING_ALERT    = "monitor/store/booking-alert";

    private static final String AVAILABILITY_STORE = "Availability Store";
    private static final String PLACE_AVAILABILITY_STORE = "Place Availability Store";
    private static final String CONSUMPTION_STORE = "Consumption Store";
    private static final String EXTERNAL_BOOKING_STORE = "External Booking Store";
    private static final String BOOKING_ALERT_STORE = "Booking Alert Store";

    private static final String PLACE_AVAILABILITY_DESCR = "Place Id";
    private static final String CONSUMPTION_DESCR = "Booking Id";
    private static final String BOOKING_ALERT_DESCR = "Booking Id";


    @RequestMapping(value = IXSI_SESSION_STATUS, method = RequestMethod.GET)
    public List<EndpointDTO> getSessionStatus() {
        log.debug("REST request for {} data", IXSI_SESSION_STATUS);
        List<EndpointDTO> endPoints = new ArrayList<>();

        Map<String, Deque<WebSocketSession>> sessionMap = webSocketSessionStore.getLookupTable();
        for (Map.Entry<String, Deque<WebSocketSession>> entry : sessionMap.entrySet()) {
            for (WebSocketSession sess : entry.getValue()) {
                EndpointDTO endPoint = new EndpointDTO();
                endPoint.setSystemId(entry.getKey());
                endPoint.setSessionId(sess.getId());
                endPoint.setOpen(sess.isOpen());

                endPoints.add(endPoint);
            }
        }
        return endPoints;
    }

    @RequestMapping(value = IXSI_STORE_LIST, method = RequestMethod.GET)
    public List<StoreDTO> getStoreList() {
        List<StoreDTO> stores = new ArrayList<>();

        StoreDTO avail = new StoreDTO();
        avail.setName(AVAILABILITY_STORE);
        avail.setLinkName(IXSI_STORE_AVAIL.substring(IXSI_STORE_AVAIL.lastIndexOf('/')+1));
        stores.add(avail);

        StoreDTO place = new StoreDTO();
        place.setName(PLACE_AVAILABILITY_STORE);
        place.setLinkName(IXSI_STORE_PLACE_AVAIL.substring(IXSI_STORE_PLACE_AVAIL.lastIndexOf('/')+1));
        stores.add(place);

        StoreDTO cons = new StoreDTO();
        cons.setName(CONSUMPTION_STORE);
        cons.setLinkName(IXSI_STORE_CONSUMPTION.substring(IXSI_STORE_CONSUMPTION.lastIndexOf('/')+1));
        stores.add(cons);

        StoreDTO ext = new StoreDTO();
        ext.setName(EXTERNAL_BOOKING_STORE);
        ext.setLinkName(IXSI_STORE_EX_BOOK.substring(IXSI_STORE_EX_BOOK.lastIndexOf('/')+1));
        stores.add(ext);

        StoreDTO balert = new StoreDTO();
        balert.setName(BOOKING_ALERT_STORE);
        balert.setLinkName(IXSI_STORE_BOOKING_ALERT.substring(IXSI_STORE_BOOKING_ALERT.lastIndexOf('/')+1));
        stores.add(balert);

        return stores;
    }

    @RequestMapping(value = IXSI_STORE_AVAIL, method = RequestMethod.GET)
    public StoreDTO<BookingTargetIDType> getAvailabilityStore() {
        log.debug("REST request for {} data", IXSI_STORE_AVAIL);
        StoreDTO<BookingTargetIDType> avail = new StoreDTO<>();
        avail.setName(AVAILABILITY_STORE);
        avail.setItemDescription(BookingTargetIDType.class.getSimpleName());
        avail.setItems(itemMapToList(availabilityStore.getLookupTable()));
        avail.setSize(avail.getItems().size());

        return avail;
    }

    @RequestMapping(value = IXSI_STORE_PLACE_AVAIL, method = RequestMethod.GET)
    public StoreDTO<String> getPlaceAvailabilityStore() {
        log.debug("REST request for {} data", IXSI_STORE_PLACE_AVAIL);
        StoreDTO<String> pAvail = new StoreDTO<>();
        pAvail.setName(PLACE_AVAILABILITY_STORE);
        pAvail.setItemDescription(PLACE_AVAILABILITY_DESCR);
        pAvail.setItems(itemMapToList(placeAvailabilityStore.getLookupTable()));
        pAvail.setSize(pAvail.getItems().size());

        return pAvail;
    }

    @RequestMapping(value = IXSI_STORE_CONSUMPTION, method = RequestMethod.GET)
    public StoreDTO<String> getConsumptionStore() {
        log.debug("REST request for {} data", IXSI_STORE_CONSUMPTION);
        StoreDTO<String> cons = new StoreDTO<>();
        cons.setName(CONSUMPTION_STORE);
        cons.setItemDescription(CONSUMPTION_DESCR);
        cons.setItems(itemMapToList(consumptionStore.getLookupTable()));
        cons.setSize(cons.getItems().size());

        return cons;
    }

    @RequestMapping(value = IXSI_STORE_EX_BOOK, method = RequestMethod.GET)
    public StoreDTO<UserInfoType> getExternalBookingStore() {
        log.debug("REST request for {} data", IXSI_STORE_EX_BOOK);
        StoreDTO<UserInfoType> exBook = new StoreDTO<>();
        exBook.setName(EXTERNAL_BOOKING_STORE);
        exBook.setItemDescription(UserInfoType.class.getSimpleName());
        exBook.setItems(itemMapToList(externalBookingStore.getLookupTable()));
        exBook.setSize(exBook.getItems().size());

        return exBook;
    }

    @RequestMapping(value = IXSI_STORE_BOOKING_ALERT, method = RequestMethod.GET)
    public StoreDTO<String> getBookingAlertStore() {
        log.debug("REST request for {} data", IXSI_STORE_BOOKING_ALERT);
        StoreDTO<String> alertStore = new StoreDTO<>();
        alertStore.setName(BOOKING_ALERT_STORE);
        alertStore.setItemDescription(BOOKING_ALERT_DESCR);
        alertStore.setItems(itemMapToList(bookingAlertStore.getLookupTable()));
        alertStore.setSize(alertStore.getItems().size());

        return alertStore;
    }

    @RequestMapping(value = IXSI_SESSION_KILL, method = RequestMethod.PUT)
    public void killSession(@PathVariable String systemId, @PathVariable String sessionId) {
        Optional<WebSocketSession> optional = webSocketSessionStore.get(systemId, sessionId);

        if (optional.isPresent()) {
            WebSocketSession session = optional.get();
            if (session.isOpen()) {
                try {
                    session.close(new CloseStatus(1000, "User requested disconnection"));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to close the session", e);
                }
            }
        }
    }

    private <T> List<StoreItem<T>> itemMapToList(Map<T, Set<String>> map) {
        List<StoreItem<T>> items = new ArrayList<>();
        for (Map.Entry<T, Set<String>> entry : map.entrySet()) {
            items.addAll(entry.getValue()
                .stream()
                .map(system -> new StoreItem<>(system, entry.getKey()))
                .collect(Collectors.toList()));
        }
        return items;
    }


}
