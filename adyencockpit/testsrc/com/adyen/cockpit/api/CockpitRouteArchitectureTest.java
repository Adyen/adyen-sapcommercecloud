package com.adyen.cockpit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.adyen.cockpit.api.CockpitRoute.Method;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Loads every route bean of the cockpit's Spring configuration and holds it to {@link CockpitRouteRules}.
 * The bean definitions are read but nothing is instantiated by Spring: the platform beans the routes depend
 * on do not exist in a unit test, and the declaration of a route is available from its no-arg constructor.
 */
@UnitTest
public class CockpitRouteArchitectureTest
{
	private static final String SPRING_FILE = "adyencockpit-spring.xml";

	@Test
	public void everyRouteOfTheCockpitSpringFileMeetsTheRules() throws Exception
	{
		final List<CockpitRoute> routes = routesOfSpringFile();

		final List<String> violations = new CockpitRouteRules().violations(routes);

		assertEquals("route violations: " + violations, List.of(), violations);
		final Set<String> served = routes.stream().map(r -> r.method() + " " + r.path()).collect(Collectors.toSet());
		assertTrue("the six cockpit routes are registered, found " + served, served.containsAll(Set.of(
				"GET /bootstrap", "GET /setup/stores", "GET /setup/merchant-accounts", "POST /setup/management-key",
				"POST /setup/merchant-account", "POST /setup/provision")));
	}

	@Test
	public void setupRoutesDeclareTheAuditActionsAndTheAdministratorAuthority() throws Exception
	{
		for (final CockpitRoute route : routesOfSpringFile())
		{
			if (route.path().startsWith("/setup/"))
			{
				assertEquals(route.toString(), "ROLE_ADMINGROUP", route.permission());
				assertEquals(route.toString(), route.method() != Method.GET, route.write());
				assertEquals(route.toString(), false, route.dangerous());
			}
		}
		final List<String> audit = routesOfSpringFile().stream().map(CockpitRoute::auditAction)
				.filter(a -> a != null).sorted().toList();
		assertEquals(List.of("setup.management-key.save", "setup.merchant-account.save", "setup.provision"), audit);
	}

	/** The rule check is shown to bite: each deliberately broken route is reported. */
	@Test
	public void brokenRoutesAreReported()
	{
		final CockpitRouteRegistryTest.Stub noPermission = new CockpitRouteRegistryTest.Stub(Method.GET, "/a", "", false, null);
		final CockpitRouteRegistryTest.Stub writeWithoutAudit = new CockpitRouteRegistryTest.Stub(Method.POST, "/b", "ROLE_X", true, " ");
		final CockpitRouteRegistryTest.Stub notMarkedWrite = new CockpitRouteRegistryTest.Stub(Method.PUT, "/c", "ROLE_X", false, "c.put");
		final CockpitRouteRegistryTest.Stub first = new CockpitRouteRegistryTest.Stub(Method.GET, "/d/{x}", "ROLE_X", false, null);
		final CockpitRouteRegistryTest.Stub second = new CockpitRouteRegistryTest.Stub(Method.GET, "/d/{y}", "ROLE_X", false, null);
		final CockpitRouteRegistryTest.Stub sameButOtherMethod = new CockpitRouteRegistryTest.Stub(Method.POST, "/d/{x}", "ROLE_X", true, "d.post");
		final CockpitRoute dangerousRead = new AbstractCockpitRoute(Method.GET, "/e", "ROLE_X", false, true, null)
		{
			@Override
			public CockpitResponse handle(final CockpitRequest request)
			{
				return null;
			}
		};

		final List<String> violations = new CockpitRouteRules().violations(
				List.of(noPermission, writeWithoutAudit, notMarkedWrite, first, second, sameButOtherMethod, dangerousRead));

		assertTrue(violations.toString(), has(violations, "GET /a", "no permission"));
		assertTrue(violations.toString(), has(violations, "POST /b", "without an audit action"));
		assertTrue(violations.toString(), has(violations, "PUT /c", "not marked write"));
		assertTrue(violations.toString(), has(violations, "GET /d/{x}", "duplicates"));
		assertTrue(violations.toString(), has(violations, "GET /d/{y}", "duplicates"));
		assertTrue(violations.toString(), has(violations, "GET /e", "dangerous but not a write"));
		assertEquals("the same path with another method is not a duplicate", 0,
				violations.stream().filter(v -> v.contains("POST /d/{x}")).count());
		assertEquals(6, violations.size());
	}

	@Test
	public void cleanRoutesHaveNoViolations()
	{
		assertEquals(List.of(), new CockpitRouteRules().violations(List.of(
				new CockpitRouteRegistryTest.Stub(Method.GET, "/a", "ROLE_X", false, null),
				new CockpitRouteRegistryTest.Stub(Method.POST, "/a", "ROLE_X", true, "a.post"))));
	}

	private static boolean has(final List<String> violations, final String route, final String text)
	{
		return violations.stream().anyMatch(v -> v.contains(route) && v.contains(text));
	}

	/** Names a property or constructor argument must never use: metadata comes from the constructor only. */
	private static final Set<String> METADATA_PROPERTIES = Set.of("method", "path", "permission", "write", "dangerous",
			"auditAction");

	private List<CockpitRoute> routesOfSpringFile() throws Exception
	{
		return routesOf(springFile());
	}

	/**
	 * The loader the architecture test uses for the real file. Beans are instantiated only when their class is
	 * a route, through the no-arg constructor, so no platform bean is needed.
	 */
	private List<CockpitRoute> routesOf(final Resource xml) throws Exception
	{
		final DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		new XmlBeanDefinitionReader(factory).loadBeanDefinitions(xml);
		final List<CockpitRoute> routes = new ArrayList<>();
		for (final String name : factory.getBeanDefinitionNames())
		{
			final BeanDefinition definition = factory.getMergedBeanDefinition(name);
			if (definition.isAbstract())
			{
				continue;
			}
			final Class<?> type;
			try
			{
				type = Class.forName(definition.getBeanClassName());
			}
			catch (final ClassNotFoundException | RuntimeException e)
			{
				throw new AssertionError("Bean '" + name + "' has no resolvable class: " + definition.getBeanClassName(), e);
			}
			if (CockpitRoute.class.isAssignableFrom(type))
			{
				assertTrue("Route bean '" + name + "' must not use constructor arguments: metadata comes from the "
						+ "no-arg constructor", definition.getConstructorArgumentValues().isEmpty());
				for (final PropertyValue value : definition.getPropertyValues().getPropertyValueList())
				{
					assertTrue("Route bean '" + name + "' sets '" + value.getName() + "', which would change what the "
							+ "route declares; metadata must come from the constructor",
							!METADATA_PROPERTIES.contains(value.getName()));
				}
				routes.add((CockpitRoute) type.getDeclaredConstructor().newInstance());
			}
		}
		return routes;
	}

	/** The loader path is shown to bite: a broken route bean in an inline XML is reported. */
	@Test
	public void loaderReportsABrokenRouteBeanOfAnXmlFile() throws Exception
	{
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><beans xmlns=\"http://www.springframework.org/schema/beans\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.springframework.org/schema/beans "
				+ "http://www.springframework.org/schema/beans/spring-beans.xsd\">"
				+ "<bean id=\"broken\" class=\"" + BrokenRoute.class.getName() + "\"/>"
				+ "<bean id=\"fine\" class=\"" + BrokenRoute.class.getName() + "\"><property name=\"label\" value=\"x\"/></bean>"
				+ "</beans>";
		final List<CockpitRoute> routes = routesOf(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));

		final List<String> violations = new CockpitRouteRules().violations(routes);

		assertEquals(2, routes.size());
		assertTrue(violations.toString(), has(violations, "POST /broken", "no permission"));
		assertTrue(violations.toString(), has(violations, "POST /broken", "without an audit action"));
		assertTrue(violations.toString(), has(violations, "POST /broken", "duplicates"));
	}

	@Test
	public void loaderFailsOnAnUnresolvableBeanClass() throws Exception
	{
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><beans xmlns=\"http://www.springframework.org/schema/beans\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.springframework.org/schema/beans "
				+ "http://www.springframework.org/schema/beans/spring-beans.xsd\">"
				+ "<bean id=\"ghost\" class=\"com.example.NoSuchRoute\"/></beans>";
		try
		{
			routesOf(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
			org.junit.Assert.fail("an unresolvable class must fail the loader");
		}
		catch (final AssertionError expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("ghost"));
		}
	}

	@Test
	public void loaderFailsWhenAPropertyWouldChangeRouteMetadata() throws Exception
	{
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><beans xmlns=\"http://www.springframework.org/schema/beans\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.springframework.org/schema/beans "
				+ "http://www.springframework.org/schema/beans/spring-beans.xsd\">"
				+ "<bean id=\"sneaky\" class=\"" + BrokenRoute.class.getName() + "\"><property name=\"permission\" value=\"x\"/></bean></beans>";
		try
		{
			routesOf(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
			org.junit.Assert.fail("a metadata property must fail the loader");
		}
		catch (final AssertionError expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("sneaky"));
		}
	}

	/** A route with a deliberately broken declaration, used by the inline XML above. */
	public static class BrokenRoute extends AbstractCockpitRoute
	{
		public BrokenRoute()
		{
			super(Method.POST, "/broken", "", true, null);
		}

		public void setLabel(final String label)
		{
			// a dependency-style property that does not touch the declaration
		}

		public void setPermission(final String permission)
		{
			// would be a way to change metadata from XML, which the architecture test forbids
		}

		@Override
		public CockpitResponse handle(final CockpitRequest request)
		{
			return null;
		}
	}

	/** On the classpath when the platform put the resources there, else next to the compiled classes. */
	private Resource springFile() throws URISyntaxException
	{
		final ClassPathResource onClasspath = new ClassPathResource(SPRING_FILE);
		if (onClasspath.exists())
		{
			return onClasspath;
		}
		final URL location = CockpitRoute.class.getProtectionDomain().getCodeSource().getLocation();
		final File classes = new File(location.toURI());
		final File extension = classes.getParentFile();
		return new FileSystemResource(new File(new File(extension, "resources"), SPRING_FILE));
	}
}
