package org.apereo.cas.authentication.bypass;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.MultifactorAuthenticationProvider;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.MultifactorAuthenticationUtils;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProviderBypassProperties;
import org.apereo.cas.services.RegisteredService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.servlet.http.HttpServletRequest;

/**
 * Multifactor Bypass based on Principal Attributes.
 *
 * @author Travis Schmidt
 * @since 6.0
 */
@Slf4j
@RequiredArgsConstructor
public class PrincipalMultifactorAuthenticationProviderBypass implements MultifactorAuthenticationProviderBypass {

    private final MultifactorAuthenticationProviderBypassProperties bypassProperties;

    @Override
    public boolean shouldExecute(final Authentication authentication,
                                 final RegisteredService registeredService,
                                 final MultifactorAuthenticationProvider provider,
                                 final HttpServletRequest request) {
        val principal = authentication.getPrincipal();
        LOGGER.debug("Evaluating multifactor authentication bypass properties for principal [{}], service [{}] and provider [{}]",
                principal.getId(), registeredService, provider);
        val bypass = MultifactorAuthenticationUtils.locateMatchingAttributeValue(bypassProperties.getPrincipalAttributeName(),
                bypassProperties.getPrincipalAttributeValue(), principal.getAttributes(), true);
        if (bypass) {
            LOGGER.debug("Bypass rules for principal [{}] indicate the request may be ignored", principal.getId());
            return false;
        }
        return true;
    }
}
