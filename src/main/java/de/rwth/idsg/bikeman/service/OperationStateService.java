package de.rwth.idsg.bikeman.service;

import com.google.common.base.Optional;
import de.rwth.idsg.bikeman.domain.Pedelec;
import de.rwth.idsg.bikeman.domain.Station;
import de.rwth.idsg.bikeman.domain.StationSlot;
import de.rwth.idsg.bikeman.ixsi.service.AvailabilityPushService;
import de.rwth.idsg.bikeman.psinterface.Utils;
import de.rwth.idsg.bikeman.psinterface.dto.OperationState;
import de.rwth.idsg.bikeman.psinterface.dto.request.PedelecStatusDTO;
import de.rwth.idsg.bikeman.psinterface.dto.request.SlotDTO;
import de.rwth.idsg.bikeman.psinterface.dto.request.StationStatusDTO;
import de.rwth.idsg.bikeman.repository.PedelecRepository;
import de.rwth.idsg.bikeman.repository.StationRepository;
import de.rwth.idsg.bikeman.repository.StationSlotRepository;
import de.rwth.idsg.bikeman.web.rest.dto.modify.CreateEditStationDTO;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xjc.schema.ixsi.TimePeriodType;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Wolfgang Kluth on 03/09/15.
 */

@Service
@Transactional
public class OperationStateService {

    @Inject private PedelecRepository pedelecRepository;
    @Inject private StationRepository stationRepository;
    @Inject private StationSlotRepository stationSlotRepository;
    @Inject private AvailabilityPushService availabilityPushService;

    public void pushStationChange(CreateEditStationDTO dto) {
        switch (dto.getState()) {
            case OPERATIVE:
                pushStationAvailability(dto.getManufacturerId());
                break;

            case INOPERATIVE:
                pushStationInavailability(dto.getManufacturerId());
                break;

            case DELETED:
                pushStationDeletion(dto.getManufacturerId());
                break;

            default:
                throw new RuntimeException("Unexpected state");
        }
    }

    public void pushSlotChange(StationSlot slot) {
        switch (slot.getState()) {
            case OPERATIVE:
                pushSlotAvailability(slot.getStation().getManufacturerId(), slot.getManufacturerId());
                break;

            case INOPERATIVE:
                pushSlotInavailability(slot.getStation().getManufacturerId(), slot.getManufacturerId());
                break;

            case DELETED:
                pushSlotDeletion(slot.getStation().getManufacturerId(), slot.getManufacturerId());
                break;

            default:
                throw new RuntimeException("Unexpected state");
        }
    }

    public void pushPedelecChange(String manufacturerId, de.rwth.idsg.bikeman.domain.OperationState state) {
        switch (state) {
            case OPERATIVE:
                pushPedelecAvailability(manufacturerId);
                break;

            case INOPERATIVE:
                pushPedelecInavailability(manufacturerId);
                break;

            case DELETED:
                pushPedelecDeletion(manufacturerId);
                break;

            default:
                throw new RuntimeException("Unexpected state");
        }
    }

    // -------------------------------------------------------------------------
    // Station
    // -------------------------------------------------------------------------

    public void pushInavailability(StationStatusDTO dto) {
        if (isInoperative(dto.getStationState())) {
            pushStationInavailability(dto.getStationManufacturerId());
            return;
        }

        if (Utils.isEmpty(dto.getSlots())) {
            return;
        }

        // For all inoperative slots, if there is a pedelec, get it's id
        List<String> pedelecManufacturerIds =
                dto.getSlots()
                   .parallelStream()
                   .filter(s -> isInoperative(s.getSlotState()))
                   .map(s -> pedelecRepository.findPedelecsByStationSlot(dto.getStationManufacturerId(), s.getSlotManufacturerId()))
                   .filter(Optional::isPresent)
                   .map(s -> s.get().getManufacturerId())
                   .collect(Collectors.toList());

        pushInavailability(dto.getStationManufacturerId(), pedelecManufacturerIds);
    }

    public void pushAvailability(StationStatusDTO dto) {
        Station station = stationRepository.findByManufacturerId(dto.getStationManufacturerId());

        if (becameOperative(station.getState(), dto.getStationState())) {
            pushStationAvailability(station.getManufacturerId());
            return;
        }

        if (Utils.isEmpty(dto.getSlots())) {
            return;
        }

        // For all slots, that changed from inoperative to operative, if there is an operative pedelec, get it's id
        List<String> pedelecManufacturerIds =
                dto.getSlots()
                   .parallelStream()
                   .map(s -> getPedelecToPush(station, s))
                   .filter(Optional::isPresent)
                   .map(p -> p.get().getManufacturerId())
                   .collect(Collectors.toList());

        pushAvailability(dto.getStationManufacturerId(), pedelecManufacturerIds);
    }

    private void pushStationAvailability(String stationManufacturerId) {
        List<Pedelec> pedelecs = pedelecRepository.findByStation(stationManufacturerId);

        List<String> pedelecManufacturerIds =
                pedelecs.parallelStream()
                        .filter(this::shouldSendAvailability)
                        .map(Pedelec::getManufacturerId)
                        .collect(Collectors.toList());

        pushAvailability(stationManufacturerId, pedelecManufacturerIds);
    }

    private void pushStationInavailability(String stationManufacturerId) {
        List<String> pedelecManufacturerIds = pedelecRepository.findManufacturerIdsByStation(stationManufacturerId);

        pushInavailability(stationManufacturerId, pedelecManufacturerIds);
    }

    private void pushStationDeletion(String stationManufacturerId) {
        throw new RuntimeException("Not implemented");
    }

    // -------------------------------------------------------------------------
    // Slot
    // -------------------------------------------------------------------------

    private void pushSlotAvailability(String stationManufacturerId, String slotManufacturerId) {
        Optional<Pedelec> pedelec = pedelecRepository.findPedelecsByStationSlot(stationManufacturerId, slotManufacturerId);

        if (pedelec.isPresent() && shouldSendAvailability(pedelec.get())) {
            pushAvailability(pedelec.get());
        }
    }

    private void pushSlotInavailability(String stationManufacturerId, String slotManufacturerId) {
        Optional<Pedelec> pedelec = pedelecRepository.findPedelecsByStationSlot(stationManufacturerId, slotManufacturerId);

        if (pedelec.isPresent()) {
            pushInavailability(pedelec.get());
        }
    }

    private void pushSlotDeletion(String stationManufacturerId, String slotManufacturerId) {
        throw new RuntimeException("Not implemented");
    }

    // -------------------------------------------------------------------------
    // Pedelec
    // -------------------------------------------------------------------------

    public void pushAvailability(PedelecStatusDTO dto) {
        if (isOperative(dto.getPedelecState())) {
            pushPedelecAvailability(dto.getPedelecManufacturerId());
        }
    }

    public void pushInavailability(PedelecStatusDTO dto) {
        if (isInoperative(dto.getPedelecState())) {
            pushPedelecInavailability(dto.getPedelecManufacturerId());
        }
    }

    public void pushPedelecAvailability(String pedelecManufacturerId) {
        Pedelec pedelec = pedelecRepository.findByManufacturerId(pedelecManufacturerId);

        if (shouldSendAvailability(pedelec)) {
            pushAvailability(pedelec);
        }
    }

    public void pushPedelecInavailability(String pedelecManufacturerId) {
        Pedelec pedelec = pedelecRepository.findByManufacturerId(pedelecManufacturerId);

        if (pedelec.getStationSlot() != null) {
            pushInavailability(pedelec);
        }
    }

    private void pushPedelecDeletion(String manufacturerId) {
        throw new RuntimeException("Not implemented");
    }

    // -------------------------------------------------------------------------
    // Private helpers, mainly for DRY
    // -------------------------------------------------------------------------

    private boolean shouldSendAvailability(Pedelec p) {
        return p.getStationSlot() != null
                && isOperative(p.getStationSlot().getState())
                && isOperative(p.getStationSlot().getStation().getState());
    }

    private boolean shouldSendAvailability(StationSlot slot, SlotDTO.StationStatus dto) {
        return slot.getPedelec() != null
                && becameOperative(slot.getState(), dto.getSlotState());
    }

    private boolean becameOperative(de.rwth.idsg.bikeman.domain.OperationState oldState, OperationState newState) {
        return !isOperative(oldState) && isOperative(newState);
    }

    private boolean isOperative(de.rwth.idsg.bikeman.domain.OperationState os) {
        return os == de.rwth.idsg.bikeman.domain.OperationState.OPERATIVE;
    }

    private boolean isOperative(OperationState os) {
        return os == OperationState.OPERATIVE;
    }

    private boolean isInoperative(OperationState os) {
        return os == OperationState.INOPERATIVE;
    }

    private Optional<Pedelec> getPedelecToPush(Station station, SlotDTO.StationStatus dto) {
        StationSlot slot = stationSlotRepository.findByManufacturerId(dto.getSlotManufacturerId(), station.getManufacturerId());

        if (shouldSendAvailability(slot, dto)) {
            return Optional.of(slot.getPedelec());
        } else {
            return Optional.absent();
        }
    }

    private void pushAvailability(Pedelec pedelec) {
        pushAvailability(
                pedelec.getStationSlot().getStation().getManufacturerId(),
                Collections.singletonList(pedelec.getManufacturerId())
        );
    }

    private void pushInavailability(Pedelec pedelec) {
        pushInavailability(
                pedelec.getStationSlot().getStation().getManufacturerId(),
                Collections.singletonList(pedelec.getManufacturerId())
        );
    }

    private TimePeriodType buildTimePeriod() {
        DateTime now = DateTime.now();

        return new TimePeriodType()
                .withBegin(now)
                .withEnd(now.plusDays(90));
    }

    // -------------------------------------------------------------------------
    // Main methods for IXSI calls
    //
    // TODO: instead of pushing each pedelec on it's own, push a list
    // -------------------------------------------------------------------------

    private void pushInavailability(String stationManufacturerId, List<String> pedelecIdList) {
        if (Utils.isEmpty(pedelecIdList)) {
            return;
        }

        TimePeriodType timePeriodType = buildTimePeriod();

        pedelecIdList.parallelStream()
                     .forEach(s -> availabilityPushService.buildAndSend(s, stationManufacturerId, timePeriodType, false));
    }

    private void pushAvailability(String stationManufacturerId, List<String> pedelecIdList) {
        if (Utils.isEmpty(pedelecIdList)) {
            return;
        }

        TimePeriodType timePeriodType = buildTimePeriod();

        pedelecIdList.parallelStream()
                     .forEach(s -> availabilityPushService.buildAndSend(s, stationManufacturerId, timePeriodType, true));
    }

}
