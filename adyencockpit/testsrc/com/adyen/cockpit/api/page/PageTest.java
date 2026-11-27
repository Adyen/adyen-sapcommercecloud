package com.adyen.cockpit.api.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitJson;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import de.hybris.bootstrap.annotations.UnitTest;
import jakarta.servlet.http.HttpServletRequest;

@UnitTest
public class PageTest
{
	private final PageTranslators translators = new PageTranslators();

	private static CockpitRequest request(final Map<String, String> params)
	{
		final HttpServletRequest http = mock(HttpServletRequest.class);
		params.forEach((k, v) -> when(http.getParameter(k)).thenReturn(v));
		return new CockpitRequest(http, Map.of(), "cid");
	}

	private static PageRequest req(final int number)
	{
		return new PageRequest(number, 20, null);
	}

	private static Map<String, Object> nextLink(final String href)
	{
		return Map.of("self", Map.of("href", "https://x/y"), "next", Map.of("href", href));
	}

	// --- PageRequest ---

	@Test
	public void defaultsApplyWhenNothingIsSent()
	{
		final PageRequest page = PageRequest.from(request(Map.of()));
		assertEquals(1, page.pageNumber());
		assertEquals(20, page.pageSize());
		assertNull(page.next());
	}

	@Test
	public void parametersAreRead()
	{
		final PageRequest page = PageRequest.from(request(Map.of("pageNumber", "3", "pageSize", "100", "next", "tok")));
		assertEquals(3, page.pageNumber());
		assertEquals(100, page.pageSize());
		assertEquals("tok", page.next());
	}

	@Test
	public void invalidValuesNameTheirField()
	{
		for (final String name : List.of(PageRequest.PAGE_NUMBER, PageRequest.PAGE_SIZE))
		{
			for (final String bad : List.of("0", "-1", "abc", "1.5", "99999999999"))
			{
				final CockpitApiException e = assertThrows(CockpitApiException.class,
						() -> PageRequest.from(request(Map.of(name, bad))));
				assertEquals(ErrorCode.INVALID_REQUEST, e.code());
				assertEquals(name, e.field());
				assertFalse(e.getMessage().contains(bad));
			}
		}
	}

	@Test
	public void pageSizeAboveTheMaximumIsRefusedNotClamped()
	{
		final CockpitApiException e = assertThrows(CockpitApiException.class,
				() -> PageRequest.from(request(Map.of("pageSize", "101"))));
		assertEquals(ErrorCode.INVALID_REQUEST, e.code());
		assertEquals("pageSize", e.field());
	}

	// --- Adyen ---

	@Test
	public void adyenZeroPagesHasNoNext()
	{
		final Page<String> page = translators.fromAdyen(List.of(), req(1), Map.of(), 0);
		assertTrue(page.items().isEmpty());
		assertNull(page.next());
		assertEquals(Long.valueOf(0), page.totalItems());
	}

	@Test
	public void adyenSinglePageHasNoNext()
	{
		final Page<String> page = translators.fromAdyen(List.of("a"), req(1), Map.of("self", Map.of("href", "h")), 1);
		assertNull(page.next());
		assertEquals(Long.valueOf(1), page.totalItems());
	}

	@Test
	public void adyenManyPagesTakeNextFromTheLink()
	{
		final Page<String> page = translators.fromAdyen(List.of("a", "b"), req(2),
				nextLink("https://management-test.adyen.com/v3/merchants?pageNumber=3&pageSize=20"), 95L);
		assertEquals("3", page.next());
		assertEquals(Long.valueOf(95), page.totalItems());
		assertEquals(2, page.pageNumber());
	}

	@Test
	public void adyenLinkThatDoesNotMoveForwardNeverLoops()
	{
		assertEquals("3", translators.fromAdyen(List.of(), req(2), nextLink("https://x/y?pageNumber=2"), null).next());
		assertEquals("3", translators.fromAdyen(List.of(), req(2), nextLink("https://x/y?pageNumber=1"), null).next());
		assertEquals("3", translators.fromAdyen(List.of(), req(2), nextLink("https://x/y?pageNumber=0"), null).next());
	}

	@Test
	public void adyenLinkWithoutPageNumberFallsBackToTheFollowingPage()
	{
		assertEquals("3", translators.fromAdyen(List.of(), req(2), nextLink("https://x/y?cursor=abc"), null).next());
		assertEquals("3", translators.fromAdyen(List.of(), req(2), nextLink("::not a uri::"), null).next());
		assertNull(translators.fromAdyen(List.of(), req(2), nextLink("https://x/y?pageNumber=2"), null).totalItems());
	}

	// --- Recurly ---

	@Test
	public void recurlyZeroOnePageAndMany()
	{
		assertNull(translators.fromRecurly(List.of(), req(1), false, null).next());
		assertNull(translators.fromRecurly(List.of("a"), req(1), false, "/ignored").next());
		final Page<String> many = translators.fromRecurly(List.of("a"), req(1), true, "/subscriptions?cursor=x");
		assertEquals("/subscriptions?cursor=x", many.next());
		assertNull(many.totalItems());
		assertNull(translators.fromRecurly(List.of("a"), req(1), true, " ").next());
	}

	// --- Chargebee ---

	@Test
	public void chargebeeZeroOnePageAndMany()
	{
		assertNull(translators.fromChargebee(List.of(), req(1), null).next());
		assertNull(translators.fromChargebee(List.of("a"), req(1), "").next());
		final Page<String> many = translators.fromChargebee(List.of("a"), req(1), "[\"1700000000\",\"42\"]");
		assertEquals("[\"1700000000\",\"42\"]", many.next());
		assertNull(many.totalItems());
	}

	// --- Page ---

	@Test
	public void jsonOmitsNullTotalAndNext() throws Exception
	{
		final JsonNode bare = CockpitJson.mapper().valueToTree(translators.fromChargebee(List.of("a"), req(1), null));
		assertFalse(bare.has("totalItems"));
		assertFalse(bare.has("next"));
		assertEquals(1, bare.get("pageNumber").asInt());
		assertEquals(20, bare.get("pageSize").asInt());
		assertEquals("a", bare.get("items").get(0).asText());

		final JsonNode full = CockpitJson.mapper().valueToTree(
				translators.fromAdyen(List.of("a"), req(1), nextLink("https://x?pageNumber=2"), 40));
		assertEquals(40, full.get("totalItems").asInt());
		assertEquals("2", full.get("next").asText());
	}

	@Test
	public void itemsAreNeverNull()
	{
		assertTrue(new Page<String>(null, 1, 20, null, null).items().isEmpty());
	}
}
