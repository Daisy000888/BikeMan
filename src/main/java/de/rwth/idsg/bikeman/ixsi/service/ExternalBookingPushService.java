package de.rwth.idsg.bikeman.ixsi.service;

import com.google.common.base.Optional;
import de.rwth.idsg.bikeman.domain.Booking;
import de.rwth.idsg.bikeman.domain.Transaction;
import de.rwth.idsg.bikeman.ixsi.IXSIConstants;
import de.rwth.idsg.bikeman.ixsi.api.Producer;
import de.rwth.idsg.bikeman.ixsi.impl.ExternalBookingStore;
import de.rwth.idsg.bikeman.ixsi.repository.IxsiUserRepository;
import de.rwth.idsg.bikeman.ixsi.schema.*;
import de.rwth.idsg.bikeman.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 24.02.2015
 */
@Slf4j
@Service
public class ExternalBookingPushService {

    @Autowired
    private Producer producer;
    @Autowired
    private ExternalBookingStore externalBookingStore;
    @Autowired
    private IxsiUserRepository ixsiUserRepository;
    @Autowired
    private BookingRepository bookingRepository;

    public void report(Booking booking, Transaction transaction) {
        String cardId = transaction.getCardAccount().getCardId();
        Optional<String> optionalMJ = ixsiUserRepository.getMajorCustomerName(cardId);

        if (optionalMJ.isPresent()) {
            UserInfoType userInfo = new UserInfoType()
                    .withUserID(cardId)
                    .withProviderID(optionalMJ.get());

            Set<String> subscribed = externalBookingStore.getSubscribedSystems(userInfo);
            if (subscribed.isEmpty()) {
                log.debug("Will not push. There is no subscribed system for user '{}'", userInfo);
                return;
            }

            // TODO improve timeperiodtype creation: default end time
            DateTime dt = transaction.getStartDateTime().toDateTime();
            TimePeriodType time = new TimePeriodType()
                    .withBegin(dt)
                    .withEnd(dt.plusHours(6));

            // create and add ixsi-booking-id to booking
            booking.setIxsiBookingId(IXSIConstants.Provider.id + IXSIConstants.BOOKING_ID_DELIMITER + booking.getBookingId());
            bookingRepository.saveAndGetId(booking);

            BookingTargetIDType bookingTarget = new BookingTargetIDType()
                    .withBookeeID(String.valueOf(transaction.getPedelec().getManufacturerId()))
                    .withProviderID(IXSIConstants.Provider.id);

            ExternalBookingType extBooking = new ExternalBookingType()
                    .withBookingID(booking.getIxsiBookingId())
                    .withBookingTargetID(bookingTarget)
                    .withUserInfo(userInfo)
                    .withTimePeriod(time);

            ExternalBookingPushMessageType bookingPush = new ExternalBookingPushMessageType().withExternalBooking(extBooking);
            SubscriptionMessageType subscriptionMessageType = new SubscriptionMessageType().withPushMessageGroup(bookingPush);
            IxsiMessageType ixsiMessageType = new IxsiMessageType().withSubscriptionMessage(subscriptionMessageType);

            producer.send(ixsiMessageType, subscribed);
        }
    }

}