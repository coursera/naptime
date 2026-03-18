package org.coursera.naptime

import org.junit.Test
import play.api.i18n.Lang
import play.api.mvc.Request
import play.api.test.FakeRequest
import org.mockito.Mockito.when
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar

class RestContextTest extends AssertionsForJUnit with MockitoSugar {

  private[this] def makeContext(languagePreferences: Seq[Lang]): RestContext[Unit, Unit] = {
    val mockRequest = mock[Request[Unit]]
    val restContext = new RestContext((), (), mockRequest, null, null, null)
    when(mockRequest.acceptLanguages).thenReturn(languagePreferences)
    restContext
  }

  def test(
      requestLanguages: Seq[Lang],
      availableLanguages: Set[Lang],
      defaultLanguage: Lang,
      expected: Lang): Unit = {
    val restContext = makeContext(requestLanguages)
    assert(restContext.selectLanguage(availableLanguages, defaultLanguage) === expected)
  }

  @Test
  def basicLanguage(): Unit = {
    test(
      requestLanguages = Seq(Lang("en")),
      availableLanguages = Set(Lang("fr"), Lang("en")),
      defaultLanguage = Lang("en"),
      expected = Lang("en"))
  }

  @Test
  def defaultFallback(): Unit = {
    test(
      requestLanguages = Seq(Lang("zh")),
      availableLanguages = Set(Lang("fr"), Lang("en")),
      defaultLanguage = Lang("en"),
      expected = Lang("en"))
  }

  @Test
  def choosePreferred(): Unit = {
    test(
      requestLanguages = Seq(Lang("zh"), Lang("fr"), Lang("en")),
      availableLanguages = Set(Lang("fr"), Lang("en")),
      defaultLanguage = Lang("en"),
      expected = Lang("fr"))
  }

  @Test
  def request_returnsUnderlyingRequest(): Unit = {
    // Exercises RestContext.request (line 54).
    val mockRequest = mock[Request[Unit]]
    val restContext = new RestContext((), (), mockRequest, null, null, null)
    when(mockRequest.acceptLanguages).thenReturn(Seq.empty)
    assert(restContext.request === mockRequest)
  }

  @Test
  def copyWithAuth_returnsCopyWithNewAuth(): Unit = {
    // Exercises RestContext.copyWithAuth (line 85).
    val mockRequest = mock[Request[Unit]]
    when(mockRequest.acceptLanguages).thenReturn(Seq.empty)
    val original = new RestContext((), (), mockRequest, null, null, null)
    val copied = original.copyWithAuth("newAuth")
    assert(copied.auth === "newAuth")
    assert(copied.body === ())
  }
}
