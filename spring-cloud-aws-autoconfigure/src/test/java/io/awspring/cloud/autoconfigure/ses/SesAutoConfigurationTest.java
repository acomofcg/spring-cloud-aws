/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.awspring.cloud.autoconfigure.ses;

import java.net.URI;
import java.util.Objects;

import io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.utils.AttributeMap;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for class {@link SesAutoConfiguration}
 *
 * @author Eddú Meléndez
 * @author Maciej Walkowiak
 * @author Arun Patra
 */
class SesAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.cloud.aws.region.static:eu-west-1")
			.withConfiguration(AutoConfigurations.of(RegionProviderAutoConfiguration.class,
					CredentialsProviderAutoConfiguration.class, SesAutoConfiguration.class));

	@Test
	void mailSenderWithJavaMail() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(MailSender.class);
			assertThat(context).hasSingleBean(JavaMailSender.class);
			assertThat(context).getBean(JavaMailSender.class).isSameAs(context.getBean(MailSender.class));
		});
	}

	@Test
	void mailSenderWithoutSesClientInTheClasspath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(software.amazon.awssdk.services.ses.SesClient.class))
				.run(context -> {
					assertThat(context).doesNotHaveBean(MailSender.class);
					assertThat(context).doesNotHaveBean(JavaMailSender.class);
				});
	}

	@Test
	void mailSenderWithSimpleEmail() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(javax.mail.Session.class)).run(context -> {
			assertThat(context).hasSingleBean(MailSender.class);
			assertThat(context).hasBean("simpleMailSender");
			assertThat(context).getBean("simpleMailSender").isSameAs(context.getBean(MailSender.class));
		});
	}

	@Test
	void sesAutoConfigurationIsDisabled() {
		this.contextRunner.withPropertyValues("spring.cloud.aws.ses.enabled:false").run(context -> {
			assertThat(context).doesNotHaveBean(MailSender.class);
			assertThat(context).doesNotHaveBean(JavaMailSender.class);
		});
	}

	@Test
	void withCustomEndpoint() {
		this.contextRunner.withPropertyValues("spring.cloud.aws.ses.endpoint:http://localhost:8090").run(context -> {
			ConfiguredAwsClient client = new ConfiguredAwsClient(context.getBean(SesClient.class));
			assertThat(client.getEndpoint()).isEqualTo(URI.create("http://localhost:8090"));
			assertThat(client.isEndpointOverridden()).isTrue();
		});
	}

	private static class ConfiguredAwsClient {

		private final AttributeMap clientConfigurationAttributes;

		ConfiguredAwsClient(SdkClient sdkClient) {
			SdkClientConfiguration clientConfiguration = (SdkClientConfiguration) ReflectionTestUtils
					.getField(sdkClient, "clientConfiguration");
			this.clientConfigurationAttributes = (AttributeMap) ReflectionTestUtils
					.getField(Objects.requireNonNull(clientConfiguration), "attributes");
		}

		URI getEndpoint() {
			return clientConfigurationAttributes.get(SdkClientOption.ENDPOINT);
		}

		boolean isEndpointOverridden() {
			return clientConfigurationAttributes.get(SdkClientOption.ENDPOINT_OVERRIDDEN);
		}

	}

}