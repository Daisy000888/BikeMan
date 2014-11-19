package de.rwth.idsg.bikeman.ixsi.processor.query;

import com.google.common.base.Optional;
import de.rwth.idsg.bikeman.ixsi.ErrorFactory;
import de.rwth.idsg.bikeman.ixsi.schema.CloseSessionRequestType;
import de.rwth.idsg.bikeman.ixsi.schema.CloseSessionResponseType;
import de.rwth.idsg.bikeman.ixsi.schema.ErrorType;
import de.rwth.idsg.bikeman.ixsi.schema.Language;
import de.rwth.idsg.bikeman.ixsi.schema.UserInfoType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 26.09.2014
 */
@Component
public class CloseSessionRequestProcessor implements
        UserRequestProcessor<CloseSessionRequestType, CloseSessionResponseType> {

    @Override
    public CloseSessionResponseType processAnonymously(CloseSessionRequestType request, Optional<Language> lan) {
        return buildError(ErrorFactory.requestNotSupported());
    }

    /**
     * This method has to validate the user infos !!!!
     */
    @Override
    public CloseSessionResponseType processForUser(CloseSessionRequestType request, Optional<Language> lan,
                                                   List<UserInfoType> userInfoList) {
        return buildError(ErrorFactory.requestNotSupported());
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Override
    public CloseSessionResponseType invalidSystem() {
        return buildError(ErrorFactory.requestNotSupported());
    }

    private CloseSessionResponseType buildError(ErrorType e) {
        CloseSessionResponseType res = new CloseSessionResponseType();
        res.getError().add(e);
        return res;
    }

}