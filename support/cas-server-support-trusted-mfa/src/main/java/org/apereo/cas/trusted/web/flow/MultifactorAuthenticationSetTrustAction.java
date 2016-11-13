package org.apereo.cas.trusted.web.flow;

import org.apache.commons.lang.StringUtils;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.CurrentCredentialsAndAuthentication;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProperties;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustRecord;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustStorage;
import org.apereo.cas.trusted.util.MultifactorAuthenticationTrustUtils;
import org.apereo.cas.web.support.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

/**
 * This is {@link MultifactorAuthenticationSetTrustAction}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class MultifactorAuthenticationSetTrustAction extends AbstractAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultifactorAuthenticationSetTrustAction.class);
    private static final String PARAM_NAME_DEVICE_NAME = "deviceName";

    private MultifactorAuthenticationTrustStorage storage;

    private TicketRegistrySupport ticketRegistrySupport;

    private MultifactorAuthenticationProperties.Trusted trustedProperties;

    @Override
    public Event doExecute(final RequestContext requestContext) throws Exception {
        final Authentication c = WebUtils.getAuthentication(requestContext);
        if (c == null) {
            LOGGER.error("Could not determine authentication from the request context");
            return error();
        }

        CurrentCredentialsAndAuthentication.bindCurrent(c);

        final String principal = c.getPrincipal().getId();
        if (!MultifactorAuthenticationTrustUtils.isMultifactorAuthenticationTrustedInScope(requestContext)) {
            LOGGER.debug("Attempt to store trusted authentication record for {}", principal);
            final MultifactorAuthenticationTrustRecord record = MultifactorAuthenticationTrustRecord.newInstance(principal,
                    MultifactorAuthenticationTrustUtils.generateGeography());

            if (requestContext.getRequestParameters().contains(PARAM_NAME_DEVICE_NAME)) {
                final String deviceName = requestContext.getRequestParameters().get(PARAM_NAME_DEVICE_NAME);
                if (StringUtils.isNotBlank(deviceName)) {
                    record.setName(deviceName);
                }
            }
            storage.set(record);
            LOGGER.debug("Saved trusted authentication record for {} under {}", principal, record.getName());
        }
        LOGGER.debug("Trusted authentication session exists for {}", principal);
        MultifactorAuthenticationTrustUtils.trackTrustedMultifactorAuthenticationAttribute(
                c,
                trustedProperties.getAuthenticationContextAttribute());
        return success();
    }

    public void setTrustedProperties(final MultifactorAuthenticationProperties.Trusted trustedProperties) {
        this.trustedProperties = trustedProperties;
    }

    public void setTicketRegistrySupport(final TicketRegistrySupport ticketRegistrySupport) {
        this.ticketRegistrySupport = ticketRegistrySupport;
    }

    public void setStorage(final MultifactorAuthenticationTrustStorage storage) {
        this.storage = storage;
    }
}
