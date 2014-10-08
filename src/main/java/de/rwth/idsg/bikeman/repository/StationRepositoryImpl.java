package de.rwth.idsg.bikeman.repository;

import de.rwth.idsg.bikeman.domain.Address;
import de.rwth.idsg.bikeman.domain.Address_;
import de.rwth.idsg.bikeman.domain.Pedelec;
import de.rwth.idsg.bikeman.domain.Pedelec_;
import de.rwth.idsg.bikeman.domain.Station;
import de.rwth.idsg.bikeman.domain.StationSlot;
import de.rwth.idsg.bikeman.domain.StationSlot_;
import de.rwth.idsg.bikeman.domain.Station_;
import de.rwth.idsg.bikeman.psinterface.dto.request.BootNotificationDTO;
import de.rwth.idsg.bikeman.psinterface.dto.request.SlotDTO;
import de.rwth.idsg.bikeman.psinterface.dto.request.StationStatusDTO;
import de.rwth.idsg.bikeman.web.rest.dto.modify.CreateEditAddressDTO;
import de.rwth.idsg.bikeman.web.rest.dto.modify.CreateEditStationDTO;
import de.rwth.idsg.bikeman.web.rest.dto.view.ViewStationDTO;
import de.rwth.idsg.bikeman.web.rest.dto.view.ViewStationSlotDTO;
import de.rwth.idsg.bikeman.web.rest.exception.DatabaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by sgokay on 26.05.14.
 */
@Repository
@Slf4j
public class StationRepositoryImpl implements StationRepository {

    private enum Operation { CREATE, UPDATE };

    @PersistenceContext private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public List<ViewStationDTO> findAll() throws DatabaseException {
        try {
            CriteriaBuilder builder = em.getCriteriaBuilder();
            CriteriaQuery<ViewStationDTO> criteria = this.getStationQuery(builder, null);
            return em.createQuery(criteria).getResultList();

        } catch (Exception e) {
            throw new DatabaseException("Failed during database operation.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStationDTO> findByLocation(BigDecimal latitude, BigDecimal longitude)  throws DatabaseException {
        // TODO
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewStationDTO findOne(long stationId) throws DatabaseException {
        CriteriaBuilder builder = em.getCriteriaBuilder();

        // get station info
        CriteriaQuery<ViewStationDTO> criteria = this.getStationQuery(builder, stationId);
        ViewStationDTO stat;
        try {
            stat = em.createQuery(criteria).getSingleResult();
        } catch (Exception e) {
            throw new DatabaseException("Failed to find station with stationId " + stationId, e);
        }

        // get slots for the station
        CriteriaQuery<ViewStationSlotDTO> slotCriteria = builder.createQuery(ViewStationSlotDTO.class);
        Root<StationSlot> stationSlot = slotCriteria.from(StationSlot.class);
        Join<StationSlot, Pedelec> pedelec = stationSlot.join(StationSlot_.pedelec, JoinType.LEFT);

        slotCriteria.select(
                builder.construct(
                        ViewStationSlotDTO.class,
                        stationSlot.get(StationSlot_.stationSlotId),
                        stationSlot.get(StationSlot_.manufacturerId),
                        stationSlot.get(StationSlot_.stationSlotPosition),
                        stationSlot.get(StationSlot_.state),
                        stationSlot.get(StationSlot_.isOccupied),
                        pedelec.get(Pedelec_.pedelecId),
                        pedelec.get(Pedelec_.manufacturerId)
                )
        ).where(builder.equal(stationSlot.get(StationSlot_.station).get(Station_.stationId), stationId));

        try {
            List<ViewStationSlotDTO> list = em.createQuery(slotCriteria).getResultList();
            stat.setSlots(list);
        } catch (Exception e) {
            throw new DatabaseException("Failed to get slots for the station", e);
        }

        return stat;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CreateEditStationDTO dto) throws DatabaseException {
        Station station = new Station();
        setFields(station, dto, Operation.CREATE);

        try {
            em.persist(station);
            log.debug("Created new manager {}", station);

        } catch (EntityExistsException e) {
            throw new DatabaseException("This station exists already.", e);

        } catch (Exception e) {
            throw new DatabaseException("Failed to create a new station.", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CreateEditStationDTO dto) throws DatabaseException {
        final Long stationId = dto.getStationId();
        if (stationId == null) {
            return;
        }

        Station station = getStationEntity(stationId);
        setFields(station,dto, Operation.UPDATE);

        try {
            em.merge(station);
            log.debug("Updated station {}", station);

        } catch (Exception e) {
            throw new DatabaseException("Failed to update station with stationId " + stationId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long stationId) throws DatabaseException {
        Station station = getStationEntity(stationId);
        try {
            em.remove(station);
            log.debug("Deleted station {}", station);
        } catch (Exception e) {
            throw new DatabaseException("Failed to delete station with stationId " + stationId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAfterBoot(BootNotificationDTO dto) throws DatabaseException {
        String stationManufacturerId = dto.getStationManufacturerId();

        // -------------------------------------------------------------------------
        // 1. Update station table
        // -------------------------------------------------------------------------

        final String sQuery = "UPDATE Station " +
                              "SET firmwareVersion = :fwVersion " +
                              "WHERE manufacturerId = :manufacturerId";

        int updateCount = em.createQuery(sQuery)
                            .setParameter("fwVersion", dto.getFirmwareVersion())
                            .setParameter("manufacturerId", stationManufacturerId)
                            .executeUpdate();

        if (updateCount == 1) {
            log.debug("[StationId: {}] Station info is updated", stationManufacturerId);
        } else {
            log.error("[StationId: {}] Station info update FAILED", stationManufacturerId);
        }

        // -------------------------------------------------------------------------
        // 2. Insert new and unknown slots
        // -------------------------------------------------------------------------

        List<SlotDTO> slotDTOs = dto.getSlotDTOs();

        Station station = (Station) em.createQuery("SELECT s FROM Station s WHERE s.manufacturerId = :manufacturerId")
                .setParameter("manufacturerId", stationManufacturerId)
                .getSingleResult();

        //TODO

        // -------------------------------------------------------------------------
        // 3. Batch update station slot table
        //
        // TODO: Find out how to batch update with JPA. Current version is BS.
        // -------------------------------------------------------------------------

        final String ssQuery = "UPDATE StationSlot " +
                               "SET state = :slotState, " +
                               "pedelec = (SELECT p FROM Pedelec p WHERE p.manufacturerId = :pedelecManufacturerId), " +
                               "isOccupied = CASE WHEN :pedelecManufacturerId IS NULL THEN false ELSE true END " +
                               "WHERE manufacturerId = :slotManufacturerId " +
                               "AND stationSlotPosition = :slotPosition";

        List<String> failedSlots = new ArrayList<>();
        for (SlotDTO temp : slotDTOs) {
            String id = temp.getSlotManufacturerId();
            int tempCount = em.createQuery(ssQuery)
                              .setParameter("slotState", temp.getSlotState())
                              .setParameter("pedelecManufacturerId", temp.getPedelecManufacturerId())
                              .setParameter("slotManufacturerId", id)
                              .setParameter("slotPosition", temp.getSlotPosition())
                              .executeUpdate();

            if (tempCount != 1) {
                failedSlots.add(id);
            }
        }

        int slotsCount = slotDTOs.size();
        if (failedSlots.size() == 0) {
            log.debug("[StationId: {}] {} slots to update, ALL are updated.",
                    stationManufacturerId, slotsCount);
        } else {
            log.error("[StationId: {}] {} slots to update, but there are failed updates. List of failed slotIds: {}",
                    stationManufacturerId, slotsCount, failedSlots);
        }
    }

//    @Override
//    public void updateStatus(StationStatusDTO dto) {
//        Station station = (Station) em.createQuery("SELECT s FROM Station s WHERE s.manufacturerId = :manufacturerId")
//                .setParameter("manufacturerId", dto.getStationManufacturerId())
//                .getSingleResult();
//
//
//        Station station = null;
//        try {
//            station = getStationEntity(1);
//            log.info("Got station: {}", station);
//
//            Set<StationSlot> slots = station.getStationSlots();
//            log.info("Got slots: {}", slots);
//
//        } catch (DatabaseException e) {
//            e.printStackTrace();
//        }
//
//
//
//        StationSlot ss = (StationSlot) em.createQuery("SELECT ss FROM StationSlot ss WHERE ss.manufacturerId IN :manufacturerId")
//                .setParameter("manufacturerId", dto.getStationManufacturerId())
//                .getSingleResult();
//
//
//    }

    /**
     * Returns a station, or throws exception when no station exists.
     *
     */
    @Transactional(readOnly = true)
    private Station getStationEntity(long stationId) throws DatabaseException {
        Station station = em.find(Station.class, stationId);
        if (station == null) {
            throw new DatabaseException("No station with stationId " + stationId);
        } else {
            return station;
        }
    }

    /**
    * This method sets the fields of the station to the values in DTO.
    *
    * Important: The ID is not set!
    */
    private void setFields(Station station, CreateEditStationDTO dto, Operation operation) {
        station.setManufacturerId(dto.getManufacturerId());
        station.setName(dto.getName());
        station.setLocationLatitude(dto.getLocationLatitude());
        station.setLocationLongitude(dto.getLocationLongitude());
        station.setNote(dto.getNote());
        station.setState(dto.getState());

        switch (operation) {
            case CREATE:
                // for create (brand new address entity)
                Address newAdd = new Address();
                CreateEditAddressDTO newDtoAdd = dto.getAddress();
                newAdd.setStreetAndHousenumber(newDtoAdd.getStreetAndHousenumber());
                newAdd.setZip(newDtoAdd.getZip());
                newAdd.setCity(newDtoAdd.getCity());
                newAdd.setCountry(newDtoAdd.getCountry());
                station.setAddress(newAdd);
                break;

            case UPDATE:
                // for edit (keep the address ID)
                Address add = station.getAddress();
                CreateEditAddressDTO dtoAdd = dto.getAddress();
                add.setStreetAndHousenumber(dtoAdd.getStreetAndHousenumber());
                add.setZip(dtoAdd.getZip());
                add.setCity(dtoAdd.getCity());
                add.setCountry(dtoAdd.getCountry());
                break;
        }
    }

    /**
    * This method returns the query to get information of all the stations or only the specified station (by stationId)
    *
    */
    private CriteriaQuery<ViewStationDTO> getStationQuery(CriteriaBuilder builder, Long stationId) {
        CriteriaQuery<ViewStationDTO> criteria = builder.createQuery(ViewStationDTO.class);

        Root<Station> station = criteria.from(Station.class);
        Join<Station, StationSlot> stationSlot = station.join(Station_.stationSlots, JoinType.LEFT);
        Join<Station, Address> address = station.join(Station_.address, JoinType.LEFT);

        Path<Boolean> occ = stationSlot.get(StationSlot_.isOccupied);

        criteria.select(
                builder.construct(
                        ViewStationDTO.class,
                        station.get(Station_.stationId),
                        station.get(Station_.manufacturerId),
                        station.get(Station_.name),
                        address.get(Address_.streetAndHousenumber),
                        address.get(Address_.zip),
                        address.get(Address_.city),
                        address.get(Address_.country),
                        station.get(Station_.locationLatitude),
                        station.get(Station_.locationLongitude),
                        station.get(Station_.note),
                        station.get(Station_.state),
                        builder.sum(builder.<Integer>selectCase()
                                        .when(builder.isFalse(occ), 1)
                                        .otherwise(0)
                        ),
                        builder.count(occ)
                )
        );

        if (stationId != null) {
            criteria.where(builder.equal(station.get(Station_.stationId), stationId));
        }

        criteria.groupBy(station.get(Station_.stationId), address.get(Address_.addressId));

        return criteria;
    }

}