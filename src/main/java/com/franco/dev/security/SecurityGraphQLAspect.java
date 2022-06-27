package com.franco.dev.security;

import graphql.GraphQLException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Aspect
@Component
@Order(1)
public class SecurityGraphQLAspect {

    /**
     * All graphQLResolver methods can be called only by authenticated user.
     *
     * @Unsecured annotated methods are excluded
     */

    @Before("allGraphQLResolverMethods() && isDefinedInApplication() && !isMethodAnnotatedAsUnsecured()")
    public void doSecurityCheck() {
        if (SecurityContextHolder.getContext() == null ||
                SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated() ||
                AnonymousAuthenticationToken.class.isAssignableFrom(SecurityContextHolder.getContext().getAuthentication().getClass())) {
            throw new GraphQLException("Sorry, you should log in first to do that!");
        }
    }

    /**
     * @AdminSecured annotated methods can be called only by admin authenticated user.
     */
    @Before("isMethodAnnotatedAsAdminUnsecured()")
    public void doAdminSecurityCheck() {
        if (!isAuthorized()) {
            throw new UnauthenticatedAccessException("Sorry, you do not have enough rights to do that!");
        }
    }


    /**
     * Matches all beans that implement {@link graphql.kickstart.tools.GraphQLQueryResolver} as
     * {@code UserMutation}, {@code UserQuery}
     * extend GraphQLResolver interface
     */
    @Pointcut("target(graphql.kickstart.tools.GraphQLQueryResolver)")
    private void allGraphQLResolverMethods() {
        //leave empty
    }

    /**
     * Matches all beans in com.franco.dev package
     */
    @Pointcut("within(com.franco.dev..*)")
    private void isDefinedInApplication() {
        //leave empty
    }

    /**
     * Any method annotated with @Unsecured
     */
    @Pointcut("@annotation(com.franco.dev.security.Unsecured)")
    private void isMethodAnnotatedAsUnsecured() {
        //leave empty
    }

    /**
     * Any method annotated with @AdminSecured
     */
    @Pointcut("@annotation(com.franco.dev.security.AdminSecured)")
    private void isMethodAnnotatedAsAdminUnsecured() {
        //leave empty
    }

    private boolean isAuthorized() {
        System.out.println("Entro al isauth");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            System.out.println("Entro al if");
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            for (GrantedAuthority auth : authorities) {
                System.out.println(auth.toString());
                if (auth.getAuthority().equals("ROLE_ADMIN"))
                    return true;
            }
        }
        return false;
    }
}
