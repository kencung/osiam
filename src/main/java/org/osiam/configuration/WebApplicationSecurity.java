/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2013-2016 tarent solutions GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.osiam.configuration;

import org.osiam.auth.login.OsiamCachingAuthenticationFailureHandler;
import org.osiam.auth.login.internal.InternalAuthenticationProvider;
import org.osiam.auth.login.ldap.OsiamLdapAuthenticationProvider;
import org.osiam.security.helper.LoginDecisionFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
@Order(2)
@EnableWebSecurity
public class WebApplicationSecurity extends WebSecurityConfigurerAdapter {

    @Autowired
    private InternalAuthenticationProvider internalAuthenticationProvider;

    @Autowired(required = false)
    private OsiamLdapAuthenticationProvider osiamLdapAuthenticationProvider;

    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/css/**", "/js/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        LoginDecisionFilter loginDecisionFilter = new LoginDecisionFilter();
        loginDecisionFilter.setAuthenticationManager(authenticationManagerBean());
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setAlwaysUseDefaultTargetUrl(false);
        loginDecisionFilter.setAuthenticationSuccessHandler(successHandler);
        loginDecisionFilter.setAuthenticationFailureHandler(
                new OsiamCachingAuthenticationFailureHandler("/login/error")
        );

        LogoutSuccessHandler logoutSuccessHandler = new SimpleUrlLogoutSuccessHandler() {
            @Override
            public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
                super.setTargetUrlParameter("post_logout_redirect_uri");
                super.onLogoutSuccess(request, response, authentication);
            }
        };

        // @formatter:off
        http.requestMatchers()
                .antMatchers("/login/**", "/error", "/oauth/**")
                .and()
            .authorizeRequests()
                .antMatchers("/login", "/login/error", "/error").permitAll()
                .anyRequest().authenticated()
                .and()
            .csrf()
                // TODO: This is a bad idea! We need CSRF at least for the `/oauth/authorize` endpoint
                // see also: https://github.com/spring-projects/spring-security-oauth/blob/2.0.8.RELEASE/samples/oauth2/sparklr/src/main/java/org/springframework/security/oauth/examples/sparklr/config/SecurityConfiguration.java#L48
                .disable()
            .exceptionHandling()
                .accessDeniedPage("/login/error")
                .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                .and()
            .formLogin()
                .loginProcessingUrl("/login/check")
                .failureUrl("/login/error")
                .loginPage("/login")
                .and()
            .logout()
                .logoutUrl("/oauth/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .and()
            .addFilterBefore(loginDecisionFilter, UsernamePasswordAuthenticationFilter.class);

        http.logout().logoutSuccessUrl("https://localhost/").invalidateHttpSession(true).logoutUrl("/oauth/logout");

        // @formatter:on
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(internalAuthenticationProvider);
        if (osiamLdapAuthenticationProvider != null) {
            auth.authenticationProvider(osiamLdapAuthenticationProvider);
        }
    }

    @Override
    @Bean(name = "authenticationManager")
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
