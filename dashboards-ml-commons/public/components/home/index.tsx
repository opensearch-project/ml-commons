/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
    EuiHorizontalRule,
    EuiPageContent,
    EuiPageContentBody,
    EuiPageContentHeader,
    EuiPageHeader,
    EuiTitle,
    EuiText,
    EuiIcon,
    EuiKeyPadMenu,
    EuiKeyPadMenuItem
} from '@elastic/eui';
import { FormattedMessage } from '@osd/i18n/react';
import { PLUGIN_NAME, PLUGIN_DESC } from '../../../common';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../common/router'
import { ComponentsCommonProps } from '../app'
interface Props extends ComponentsCommonProps {

}

export const Home = ({ }: Props) => {
    return (
        <>
            <EuiPageHeader>
                <EuiTitle size="l">
                    <h1>
                        <FormattedMessage
                            id="mlCommonsPlugin.helloWorldText"
                            defaultMessage="{name}"
                            values={{ name: PLUGIN_NAME }}
                        />
                    </h1>
                </EuiTitle>
            </EuiPageHeader>
            <EuiPageContent>
                <EuiPageContentHeader>
                    <EuiTitle>
                        <h2>
                            <FormattedMessage
                                id="mlCommonsPlugin.congratulationsTitle"
                                defaultMessage="Congratulations, you have successfully created a new OpenSearch Dashboards Plugin!"
                            />
                        </h2>
                    </EuiTitle>
                </EuiPageContentHeader>
                <EuiPageContentBody>
                    <EuiText>
                        <p>
                            <FormattedMessage
                                id="mlCommonsPlugin.content"
                                defaultMessage={PLUGIN_DESC}
                            />
                        </p>
                    </EuiText>
                    <EuiHorizontalRule margin="l" />

                    <nav aria-label="Nav title">
                        <EuiKeyPadMenu>
                            {ROUTES.map(({ path, icon, label }) => {
                                return icon ? (
                                    <Link to={path}>
                                        <EuiKeyPadMenuItem label={label}>
                                            <EuiIcon type={icon} size="xxl" />
                                        </EuiKeyPadMenuItem>
                                    </Link>
                                ) : null
                            })}
                        </EuiKeyPadMenu>
                    </nav>
                </EuiPageContentBody>
            </EuiPageContent>
        </>
    )
}