package org.apereo.cas.authentication.bypass;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.MultifactorAuthenticationProvider;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderBypass;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProviderBypassProperties;
import org.apereo.cas.services.RegisteredService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Multifactor Bypass provider based on Credentials.
 *
 * @author Travis Schmidt
 * @since 6.0
 */
@Slf4j
@RequiredArgsConstructor
public class CredentialMultifactorAuthenticationProviderBypass implements MultifactorAuthenticationProviderBypass {

    private final MultifactorAuthenticationProviderBypassProperties bypassProperties;

    @Override
    public boolean shouldExecute(final Authentication authentication,
                                 final RegisteredService registeredService,
                                 final MultifactorAuthenticationProvider provider,
                                 final HttpServletRequest request) {
        val bypassByCredType = locateMatchingCredentialType(authentication, bypassProperties.getCredentialClassType());
        if (bypassByCredType) {
            LOGGER.debug("Bypass rules for credential types [{}] indicate the request may be ignored", bypassProperties.getCredentialClassType());
            return false;
        }

        return true;
    }

    /**
     * Locate matching credential type boolean.
     *
     * @param authentication      the authentication
     * @param credentialClassType the credential class type
     * @return the boolean
     */
    protected boolean locateMatchingCredentialType(final Authentication authentication, final String credentialClassType) {
        return StringUtils.isNotBlank(credentialClassType) && authentication.getCredentials()
                .stream()
                .anyMatch(e -> e.getCredentialClass().getName().matches(credentialClassType));
    }
}
