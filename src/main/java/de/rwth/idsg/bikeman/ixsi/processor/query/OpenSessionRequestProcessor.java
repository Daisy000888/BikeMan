package de.rwth.idsg.bikeman.ixsi.processor.query;

import com.google.common.base.Optional;
import de.rwth.idsg.bikeman.ixsi.ErrorFactory;
import de.rwth.idsg.bikeman.ixsi.schema.ErrorType;
import de.rwth.idsg.bikeman.ixsi.schema.Language;
import de.rwth.idsg.bikeman.ixsi.schema.OpenSessionRequestType;
import de.rwth.idsg.bikeman.ixsi.schema.OpenSessionResponseType;
import de.rwth.idsg.bikeman.ixsi.schema.UserInfoType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 26.09.2014
 */
@Component
public class OpenSessionRequestProcessor implements
        UserRequestProcessor<OpenSessionRequestType, OpenSessionResponseType> {

    @Override
    public OpenSessionResponseType processAnonymously(OpenSessionRequestType request, Optional<Language> lan) {
        return buildError(ErrorFactory.requestNotSupported());
    }

    /**
     * This method has to validate the user infos !!!!
     */
    @Override
    public OpenSessionResponseType processForUser(OpenSessionRequestType request, Optional<Language> lan,
                                                  List<UserInfoType> userInfoList) {
        return buildError(ErrorFactory.requestNotSupported());
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Override
    public OpenSessionResponseType invalidSystem() {
        return buildError(ErrorFactory.requestNotSupported());
    }

    private OpenSessionResponseType buildError(ErrorType e) {
        OpenSessionResponseType res = new OpenSessionResponseType();
        res.getError().add(e);
        return res;
    }
}