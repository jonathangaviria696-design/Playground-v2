/**
 * Configuración central de Spring Security para la aplicación Playground.
 * <p>
 * Define codificación de contraseñas, proveedor DAO, cadena de filtros HTTP,
 * reglas de autorización por ruta, login por formulario, OAuth2 con Google,
 * gestión de sesiones, logout con borrado de cookie JWT y respuestas 401/redirect según API o web.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.proyecto.aula.playgroundv2.service.UsuarioService;
import com.proyecto.aula.playgroundv2.security.JwtAuthenticationFilter;
import com.proyecto.aula.playgroundv2.security.JwtCookieService;
import com.proyecto.aula.playgroundv2.security.OAuth2AuthenticationSuccessHandler;

/**
 * Beans y {@link SecurityFilterChain} que gobiernan quién puede acceder a cada URL y cómo se autentica.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        /** Redirección y emisión de JWT tras login clásico usuario/contraseña. */
        @Autowired
        private CustomSuccessHandler customSuccessHandler;

        // --- Beans de infraestructura de autenticación ---

        /**
         * Codificador BCrypt para hashear contraseñas en registro y validación.
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * Proveedor que valida credenciales contra {@link UsuarioService} y el password encoder.
         */
        @Bean
        public DaoAuthenticationProvider authenticationProvider(
                        UsuarioService userDetailsService,
                        PasswordEncoder passwordEncoder) {

                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setUserDetailsService(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);

                return provider;
        }

        /**
         * Expone el {@link AuthenticationManager} para uso en controladores o servicios si hiciera falta.
         */
        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        /**
         * Publica eventos de sesión HTTP (útil con control de sesiones concurrentes).
         */
        @Bean
        public static HttpSessionEventPublisher httpSessionEventPublisher() {
                return new HttpSessionEventPublisher();
        }

        // --- Cadena principal de seguridad web ---

        /**
         * Configura {@link HttpSecurity}: rutas públicas, APIs, rol ADMIN, JWT filter, login y logout.
         * <p>
         * Cómo funciona: combina autorización por matcher, form login con {@link CustomSuccessHandler},
         * OAuth2 con {@link OAuth2AuthenticationSuccessHandler}, sesión IF_REQUIRED con límite de
         * una sesión por usuario, y añade {@link JwtAuthenticationFilter} antes del filtro de username/password.
         * </p>
         */
        @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        DaoAuthenticationProvider authenticationProvider,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        JwtCookieService jwtCookieService,
                        OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler) throws Exception {

                HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
                requestCache.setMatchingRequestParameterName(null);

                http
                                .authenticationProvider(authenticationProvider)

                                // Reglas de acceso por patrón de URL
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/login",
                                                                "/registro",
                                                                "/dashboard",
                                                                "/buscar",
                                                                "/juegos",
                                                                "/juegos/**",
                                                                "/css/**",
                                                                "/js/**",
                                                                "/images/**",
                                                                "/uploads/**",
                                                                "/oauth2/**",
                                                                "/login/oauth2/**")
                                                .permitAll()
                                                .requestMatchers("/api/auth/**", "/api/juegos/**").permitAll()
                                                .requestMatchers("/api/usuarios/**", "/api/wishlist/**").authenticated()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/wishlist/**").authenticated()
                                                .anyRequest().authenticated())

                                .requestCache(cache -> cache.requestCache(requestCache))

                                // Login tradicional con página /login
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .successHandler(customSuccessHandler)
                                                .permitAll())

                                // Sesión HTTP opcional + una sesión activa por usuario
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                .sessionFixation(fix -> fix.migrateSession())
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false)
                                                .expiredUrl("/login?expired"))

                                // Login social Google
                                .oauth2Login(oauth -> oauth
                                                .loginPage("/login")
                                                .successHandler(oAuth2AuthenticationSuccessHandler))

                                // Logout: borra cookie JWT y redirige
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .addLogoutHandler(
                                                                (request, response, authentication) -> jwtCookieService
                                                                                .clearJwtCookie(response))
                                                .logoutSuccessUrl("/login?logout")
                                                .permitAll())

                                // API → JSON 401; vistas → redirect a /login
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        String uri = request.getRequestURI();
                                                        if (uri.startsWith("/api/")) {
                                                                response.setStatus(401);
                                                                response.setContentType("application/json");
                                                                response.getWriter()
                                                                                .write("{\"error\":\"No autorizado\"}");
                                                        } else {
                                                                response.sendRedirect("/login");
                                                        }
                                                }));

                http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
