/*
 *  Copyright 2017 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.plugin.github.authentication;

import io.curity.identityserver.plugin.github.config.GitHubAuthenticatorPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.attribute.Attribute;
import se.curity.identityserver.sdk.authentication.AuthenticationResult;
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider;
import se.curity.identityserver.sdk.web.Request;
import se.curity.identityserver.sdk.web.Response;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.curity.identityserver.plugin.github.authentication.RedirectUriUtil.createRedirectUri;
import static se.curity.identityserver.sdk.http.RedirectStatusCode.MOVED_TEMPORARILY;

public class GitHubAuthenticatorRequestHandler implements AuthenticatorRequestHandler<Request>
{
    private static final Logger _logger = LoggerFactory.getLogger(GitHubAuthenticatorRequestHandler.class);
    private static final String AUTHORIZATION_ENDPOINT = "https://github.com/login/oauth/authorize";

    private final GitHubAuthenticatorPluginConfig _config;
    private final ExceptionFactory _exceptionFactory;
    private final AuthenticatorInformationProvider _authenticatorInformationProvider;

    public GitHubAuthenticatorRequestHandler(GitHubAuthenticatorPluginConfig config)
    {
        _config = config;
        _exceptionFactory = config.getExceptionFactory();
        _authenticatorInformationProvider = config.getAuthenticatorInformationProvider();
    }

    @Override
    public Optional<AuthenticationResult> get(Request request, Response response)
    {
        _logger.info("GET request received for authentication authentication");

        String redirectUri = createRedirectUri(_authenticatorInformationProvider, _exceptionFactory);
        String state = UUID.randomUUID().toString();
        Map<String, Collection<String>> queryStringArguments = new LinkedHashMap<>(5);
        Set<String> scopes = new LinkedHashSet<>(14);

        _config.getSessionManager().put(Attribute.of("state", state));

        addQueryString(queryStringArguments, "client_id", _config.getClientId());
        addQueryString(queryStringArguments, "redirect_uri", redirectUri);
        addQueryString(queryStringArguments, "state", state);
        addQueryString(queryStringArguments, "response_type", "code");

        manageScopes(scopes);

        addQueryString(queryStringArguments, "scope", String.join(" ", scopes));

        _logger.debug("Redirecting to {} with query string arguments {}", AUTHORIZATION_ENDPOINT,
                queryStringArguments);

        throw _exceptionFactory.redirectException(AUTHORIZATION_ENDPOINT, MOVED_TEMPORARILY,
                queryStringArguments, false);
    }

    private void manageScopes(Set<String> scopes)
    {
        _config.getManageOrganization().ifPresent(manageOrganization ->
        {
            switch (manageOrganization.getAccess())
            {
                case WRITE:
                    scopes.add("write:org");
                    break;
                case READ_WRITE:
                    scopes.add("admin:org");
                case READ:
                default:
                    scopes.add("read:org");
            }
        });
        _config.getManageRepo().ifPresent(manageRepo ->
        {
            if (manageRepo.isDeploymentStatusesAccess() && manageRepo.isPublicReposAccess()
                    && manageRepo.isInviteAccess() && manageRepo.isReadWriteCommitStatus())
            {
                scopes.add("repo");
            }
            else
            {
                if (manageRepo.isDeploymentStatusesAccess())
                {
                    scopes.add("repo_deployment");
                }

                if (manageRepo.isInviteAccess())
                {
                    scopes.add("repo:invite");
                }

                if (manageRepo.isPublicReposAccess())
                {
                    scopes.add("public_repo");
                }

                if (manageRepo.isReadWriteCommitStatus())
                {
                    scopes.add("repo:status");
                }
            }
        });

        switch (_config.getPublicKeysAccess())
        {
            case WRITE:
                scopes.add("write:public_key");
                break;
            case READ_WRITE:
                scopes.add("admin:public_key");
                break;
            case READ:
                scopes.add("read:public_key");
        }

        switch (_config.getRepoHooksAccess())
        {
            case WRITE:
                scopes.add("write:repo_hook");
                break;
            case READ_WRITE:
                scopes.add("admin:repo_hook");
                break;
            case READ:
                scopes.add("read:repo_hook");
        }

        if (_config.isOrganizationHooks())
        {
            scopes.add("admin:org_hook");
        }

        if (_config.isGistsAccess())
        {
            scopes.add("gist");
        }

        if (_config.isNotificationsAccess())
        {
            scopes.add("notifications");
        }

        _config.getManageUser().ifPresent(manageUser ->
        {
            if (manageUser.isEmailAccess() && manageUser.isFollowAccess())
            {
                scopes.add("user");
            }
            else
            {
                scopes.add("read:user");

                if (manageUser.isEmailAccess())
                {
                    scopes.add("user:email");
                }

                if (manageUser.isFollowAccess())
                {
                    scopes.add("user:follow");
                }
            }
        });

        if (_config.isDeleteRepo())
        {
            scopes.add("delete_repo");
        }

        switch (_config.getGpgKeysAccess())
        {
            case WRITE:
                scopes.add("write:gpg_key");
                break;
            case READ_WRITE:
                scopes.add("admin:gpg_key");
                break;
            case READ:
                scopes.add("read:gpg_key");
        }
    }

    @Override
    public Optional<AuthenticationResult> post(Request request, Response response)
    {
        throw _exceptionFactory.methodNotAllowed();
    }

    @Override
    public Request preProcess(Request request, Response response)
    {
        return request;
    }

    private static void addQueryString(Map<String, Collection<String>> queryStringArguments, String key, Object value)
    {
        queryStringArguments.put(key, Collections.singleton(value.toString()));
    }
}
