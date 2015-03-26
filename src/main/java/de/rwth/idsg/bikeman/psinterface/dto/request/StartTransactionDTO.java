package de.rwth.idsg.bikeman.psinterface.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by swam on 31/07/14.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartTransactionDTO {
    private String cardId;
    private String pedelecManufacturerId;
    private String stationManufacturerId;
    private String slotManufacturerId;
    private Long timestamp;
}
