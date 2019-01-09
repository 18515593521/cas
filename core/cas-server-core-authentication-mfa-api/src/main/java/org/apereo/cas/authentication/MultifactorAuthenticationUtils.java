package org.apereo.cas.authentication;

import org.apereo.cas.authentication.bypass.AuthenticationMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.ChainingMultifactorAuthenticationBypassProvider;
import org.apereo.cas.authentication.bypass.CredentialMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.GroovyMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.HttpRequestMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.PrincipalMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.RestMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.bypass.ServiceMultifactorAuthenticationProviderBypass;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProviderBypassProperties;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import org.apache.commons.lang3.StringUtils;

import org.springframework.context.ApplicationContext;
import org.springframework.webflow.core.collection.LocalAttributeMap;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is {@link MultifactorAuthenticationUtils}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Slf4j
@UtilityClass
public class MultifactorAuthenticationUtils {

    /**
     * Build event attribute map map.
     *
     * @param principal the principal
     * @param service   the service
     * @param provider  the provider
     * @return the map
     */
    public static Map<String, Object> buildEventAttributeMap(final Principal principal,
                                                             final Optional<RegisteredService> service,
                                                             final MultifactorAuthenticationProvider provider) {
        val map = new HashMap<String, Object>();
        map.put(Principal.class.getName(), principal);
        service.ifPresent(svc -> map.put(RegisteredService.class.getName(), svc));
        map.put(MultifactorAuthenticationProvider.class.getName(), provider);
        return map;
    }

    /**
     * Validate event id for matching transition in context event.
     *
     * @param eventId    the event id
     * @param context    the context
     * @param attributes the attributes
     * @return the event
     */
    public static Event validateEventIdForMatchingTransitionInContext(final String eventId,
                                                                      final Optional<RequestContext> context,
                                                                      final Map<String, Object> attributes) {
        val attributesMap = new LocalAttributeMap<Object>(attributes);
        val event = new Event(eventId, eventId, attributesMap);

        return context.map(ctx -> {
            val def = ctx.getMatchingTransition(event.getId());
            if (def == null) {
                throw new AuthenticationException("Transition definition cannot be found for event " + event.getId());
            }
            return event;
        }).orElse(event);
    }


    /**
     * Resolve event via multivalued attribute set.
     *
     * @param principal      the principal
     * @param attributeValue the attribute value
     * @param service        the service
     * @param context        the context
     * @param provider       the provider
     * @param predicate      the predicate
     * @return the set
     */
    public static Set<Event> resolveEventViaMultivaluedAttribute(final Principal principal,
                                                                 final Object attributeValue,
                                                                 final RegisteredService service,
                                                                 final Optional<RequestContext> context,
                                                                 final MultifactorAuthenticationProvider provider,
                                                                 final Predicate<String> predicate) {
        val events = new HashSet<Event>();
        if (attributeValue instanceof Collection) {
            val values = (Collection<String>) attributeValue;
            values.forEach(value -> {
                try {
                    if (predicate.test(value)) {
                        val id = provider.getId();
                        val event = validateEventIdForMatchingTransitionInContext(id, context, buildEventAttributeMap(principal, Optional.of(service), provider));
                        events.add(event);
                    }
                } catch (final Exception e) {
                    LOGGER.debug("Ignoring [{}] since no matching transition could be found", value);
                }
            });
            return (Set) events;
        }

        return null;
    }

    /**
     * Resolve provider optional.
     *
     * @param providers        the providers
     * @param requestMfaMethod the request mfa method
     * @return the optional
     */
    public static Optional<MultifactorAuthenticationProvider> resolveProvider(final Map<String, MultifactorAuthenticationProvider> providers,
                                                                       final Collection<String> requestMfaMethod) {
        return providers.values()
            .stream()
            .filter(p -> requestMfaMethod.stream().filter(Objects::nonNull).anyMatch(p::matches))
            .findFirst();
    }

    /**
     * Resolve provider optional.
     *
     * @param providers        the providers
     * @param requestMfaMethod the request mfa method
     * @return the optional
     */
    public static Optional<MultifactorAuthenticationProvider> resolveProvider(final Map<String, MultifactorAuthenticationProvider> providers,
                                                                       final String requestMfaMethod) {
        return resolveProvider(providers, Stream.of(requestMfaMethod).collect(Collectors.toList()));
    }

    /**
     * New multifactor authentication provider bypass multifactor.
     *
     * @param props the props
     * @return the multifactor authentication provider bypass
     */
    public static MultifactorAuthenticationProviderBypass newMultifactorAuthenticationProviderBypass(
        final MultifactorAuthenticationProviderBypassProperties props) {

        val bypass = new ChainingMultifactorAuthenticationBypassProvider();
        bypass.addBypass(new ServiceMultifactorAuthenticationProviderBypass(props));

        if (StringUtils.isNotBlank(props.getPrincipalAttributeName())) {
            bypass.addBypass(new PrincipalMultifactorAuthenticationProviderBypass(props));
        }
        if (StringUtils.isNotBlank(props.getAuthenticationAttributeName())
            || StringUtils.isNotBlank(props.getAuthenticationHandlerName())
            || StringUtils.isNotBlank(props.getAuthenticationMethodName())) {
            bypass.addBypass(new AuthenticationMultifactorAuthenticationProviderBypass(props));
        }
        if (StringUtils.isNotBlank(props.getCredentialClassType())) {
            bypass.addBypass(new CredentialMultifactorAuthenticationProviderBypass(props));
        }
        if (StringUtils.isNotBlank(props.getHttpRequestHeaders()) || StringUtils.isNotBlank(props.getHttpRequestRemoteAddress())) {
            bypass.addBypass(new HttpRequestMultifactorAuthenticationProviderBypass(props));
        }
        if (props.getGroovy() != null) {
            bypass.addBypass(new GroovyMultifactorAuthenticationProviderBypass(props));
        }
        if (props.getRest() != null) {
            bypass.addBypass(new RestMultifactorAuthenticationProviderBypass(props));
        }
        return bypass;
    }

    /**
     * Resolve event via single attribute set.
     *
     * @param principal      the principal
     * @param attributeValue the attribute value
     * @param service        the service
     * @param context        the context
     * @param provider       the provider
     * @param predicate      the predicate
     * @return the set
     */
    @SneakyThrows
    public static Set<Event> resolveEventViaSingleAttribute(final Principal principal,
                                                     final Object attributeValue,
                                                     final RegisteredService service,
                                                     final Optional<RequestContext> context,
                                                     final MultifactorAuthenticationProvider provider,
                                                     final Predicate<String> predicate) {
        if (attributeValue instanceof String) {
            LOGGER.debug("Attribute value [{}] is a single-valued attribute", attributeValue);
            if (predicate.test((String) attributeValue)) {
                LOGGER.debug("Attribute value predicate [{}] has matched the [{}]", predicate, attributeValue);
                return evaluateEventForProviderInContext(principal, service, context, provider);
            }
            LOGGER.debug("Attribute value predicate [{}] could not match the [{}]", predicate, attributeValue);

        }
        LOGGER.debug("Attribute value [{}] is not a single-valued attribute", attributeValue);
        return null;
    }


    /**
     * Gets authentication provider for service.
     *
     * @param service the service
     * @return the authentication provider for service
     */
    public Collection<MultifactorAuthenticationProvider> getAuthenticationProviderForService(final RegisteredService service) {
        val policy = service.getMultifactorPolicy();
        if (policy != null) {
            return policy.getMultifactorAuthenticationProviders().stream()
                .map(MultifactorAuthenticationUtils::getMultifactorAuthenticationProviderFromApplicationContext)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        }
        return null;
    }

    /**
     * Find the MultifactorAuthenticationProvider in the application contact that matches the specified providerId (e.g. "mfa-duo").
     *
     * @param providerId the provider id
     * @return the registered service multifactor authentication provider
     */
    public static Optional<MultifactorAuthenticationProvider> getMultifactorAuthenticationProviderFromApplicationContext(final String providerId) {
        try {
            LOGGER.trace("Locating bean definition for [{}]", providerId);
            return MultifactorAuthenticationUtils.getAvailableMultifactorAuthenticationProviders(ApplicationContextProvider.getApplicationContext()).values().stream()
                .filter(p -> p.matches(providerId))
                .findFirst();
        } catch (final Exception e) {
            LOGGER.trace("Could not locate [{}] bean id in the application context as an authentication provider.", providerId);
        }
        return Optional.empty();
    }

    /**
     * Evaluate event for provider in context set.
     *
     * @param principal the principal
     * @param service   the service
     * @param context   the context
     * @param provider  the provider
     * @return the set
     */
    public static Set<Event> evaluateEventForProviderInContext(final Principal principal,
                                                        final RegisteredService service,
                                                        final Optional<RequestContext> context,
                                                        final MultifactorAuthenticationProvider provider) {
        LOGGER.debug("Attempting check for availability of multifactor authentication provider [{}] for [{}]", provider, service);
        if (provider != null) {
            LOGGER.debug("Provider [{}] is successfully verified", provider);
            val id = provider.getId();
            val event = MultifactorAuthenticationUtils.validateEventIdForMatchingTransitionInContext(id, context,
                MultifactorAuthenticationUtils.buildEventAttributeMap(principal, Optional.of(service), provider));
            return CollectionUtils.wrapSet(event);
        }
        LOGGER.debug("Provider could not be verified");
        return new HashSet<>(0);
    }

    /**
     * Gets all multifactor authentication providers from application context.
     *
     * @param applicationContext the application context
     * @return the all multifactor authentication providers from application context
     */
    public static Map<String, MultifactorAuthenticationProvider> getAvailableMultifactorAuthenticationProviders(
        final ApplicationContext applicationContext) {
        try {
            return applicationContext.getBeansOfType(MultifactorAuthenticationProvider.class, false, true);
        } catch (final Exception e) {
            LOGGER.trace("No beans of type [{}] are available in the application context. "
                    + "CAS may not be configured to handle multifactor authentication requests in absence of a provider",
                MultifactorAuthenticationProvider.class);
        }
        return new HashMap<>(0);
    }

    /**
     * Method returns an Optional that will contain a {@link MultifactorAuthenticationProvider} that has the
     * same id as the passed providerId parameter.
     *
     * @param providerId - the id to match
     * @param context    - ApplicationContext
     * @return - Optional
     */
    public static Optional<MultifactorAuthenticationProvider> getMultifactorAuthenticationProviderById(final String providerId,
                                                                                                       final ApplicationContext context) {
        return getAvailableMultifactorAuthenticationProviders(context).values()
            .stream()
            .filter(p -> p.matches(providerId))
            .findFirst();
    }

    /**
     * Evaluate attribute rules for bypass.
     *
     * @param attrName               the attr name
     * @param attrValue              the attr value
     * @param attributes             the attributes
     * @param matchIfNoValueProvided the force match on value
     * @return true a matching attribute name/value is found
     */
    public static boolean locateMatchingAttributeValue(final String attrName, final String attrValue,
                                                   final Map<String, Object> attributes,
                                                   final boolean matchIfNoValueProvided) {
        LOGGER.debug("Locating matching attribute [{}] with value [{}] amongst the attribute collection [{}]", attrName, attrValue, attributes);
        if (StringUtils.isBlank(attrName)) {
            LOGGER.debug("Failed to match since attribute name is undefined");
            return false;
        }

        val names = attributes.entrySet()
                .stream()
                .filter(e -> {
                    LOGGER.debug("Attempting to match [{}] against [{}]", attrName, e.getKey());
                    return e.getKey().matches(attrName);
                })
                .collect(Collectors.toSet());

        LOGGER.debug("Found [{}] attributes relevant for multifactor authentication bypass", names.size());

        if (names.isEmpty()) {
            return false;
        }

        if (StringUtils.isBlank(attrValue)) {
            LOGGER.debug("No attribute value to match is provided; Match result is set to [{}]", matchIfNoValueProvided);
            return matchIfNoValueProvided;
        }

        val values = names
                .stream()
                .filter(e -> {
                    val valuesCol = CollectionUtils.toCollection(e.getValue());
                    LOGGER.debug("Matching attribute [{}] with values [{}] against [{}]", e.getKey(), valuesCol, attrValue);
                    return valuesCol
                            .stream()
                            .anyMatch(v -> v.toString().matches(attrValue));
                }).collect(Collectors.toSet());

        LOGGER.debug("Matching attribute values remaining are [{}]", values);
        return !values.isEmpty();
    }
}
