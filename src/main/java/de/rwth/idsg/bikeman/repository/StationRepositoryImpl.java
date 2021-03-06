package de.rwth.idsg.bikeman.repository;

import de.rwth.idsg.bikeman.domain.Address;
import de.rwth.idsg.bikeman.domain.Address_;
import de.rwth.idsg.bikeman.domain.OperationState;
import de.rwth.idsg.bikeman.domain.Pedelec;
import de.rwth.idsg.bikeman.domain.Pedelec_;
import de.rwth.idsg.bikeman.domain.Station;
import de.rwth.idsg.bikeman.domain.StationSlot;
import de.rwth.idsg.bikeman.domain.StationSlot_;
import de.rwth.idsg.bikeman.domain.Station_;
import de.rwth.idsg.bikeman.web.rest.dto.modify.CreateEditAddressDTO;
import de.rwth.idsg.bikeman.web.rest.dto.modify.CreateEditStationDTO;
import de.rwth.idsg.bikeman.web.rest.dto.view.ViewErrorDTO;
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
import java.util.List;

/**
 * Created by sgokay on 26.05.14.
 */
@Repository
@Slf4j
public class StationRepositoryImpl implements StationRepository {

    private enum Operation {CREATE, UPDATE}

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Station findByManufacturerId(String manufacturerId) throws DatabaseException {
        final String q = "SELECT s FROM Station s WHERE s.manufacturerId = :manufacturerId";
        try {
            return em.createQuery(q, Station.class)
                .setParameter("manufacturerId", manufacturerId)
                .getSingleResult();
        } catch (Exception e) {
            throw new DatabaseException("Failed to find station with manufacturerId " + manufacturerId, e);
        }
    }

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
    @Transactional(readOnly = true)
    public List<ViewErrorDTO> findErrors() throws DatabaseException {
        final String q = "SELECT new de.rwth.idsg.bikeman.web.rest.dto.view.ViewErrorDTO(" +
                "s.stationId, s.manufacturerId, s.name, s.errorCode, s.errorInfo, s.updated) " +
                "FROM Station s where not (s.errorCode = '') and s.errorCode is not null";
        try {
            return em.createQuery(q, ViewErrorDTO.class)
                     .getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Failed to find stations with error messages");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getEndpointAddress(long stationId) throws DatabaseException {
        final String q = "SELECT s.endpointAddress FROM Station s WHERE s.stationId = :stationId";
        try {
            return em.createQuery(q, String.class)
                     .setParameter("stationId", stationId)
                     .getSingleResult();
        } catch (Exception e) {
            throw new DatabaseException("Failed to find station with stationId " + stationId, e);
        }
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
        setFields(station, dto, Operation.UPDATE);

        try {
            em.merge(station);
            log.debug("Updated station {}", station);

        } catch (Exception e) {
            throw new DatabaseException("Failed to update station with stationId " + stationId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeSlotState(long stationId, int slotPosition, OperationState state) {
        final String query = "UPDATE StationSlot ss " +
                "SET ss.state = :state " +
                "WHERE ss.stationSlotPosition = :slotPosition " +
                "AND ss.station = (SELECT s FROM Station s WHERE s.stationId = :stationId)";

        int count = em.createQuery(query)
                .setParameter("state", state)
                .setParameter("slotPosition", slotPosition)
                .setParameter("stationId", stationId)
                .executeUpdate();

        if (count == 1) {
            log.debug("[StationId: {}] Slot with position {} is updated", stationId, slotPosition);
        } else {
            log.error("[StationId: {}] Slot with position {} update FAILED", stationId, slotPosition);
        }
    }

    /**
     * Returns a station, or throws exception when no station exists.
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
     * <p/>
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
