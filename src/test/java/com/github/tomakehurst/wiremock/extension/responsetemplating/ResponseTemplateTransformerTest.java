package com.github.tomakehurst.wiremock.extension.responsetemplating;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.matching.MockRequest.mockRequest;
import static com.github.tomakehurst.wiremock.testsupport.NoFileSource.noFileSource;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ResponseTemplateTransformerTest {

    private ResponseTemplateTransformer transformer;

    @Before
    public void setup() {
        transformer = new ResponseTemplateTransformer(true);
    }

    @Test
    public void queryParameters() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things?multi_param=one&multi_param=two&single-param=1234"),
            aResponse().withBody(
                "Multi 1: {{request.query.multi_param.[0]}}, Multi 2: {{request.query.multi_param.[1]}}, Single 1: {{request.query.single-param}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "Multi 1: one, Multi 2: two, Single 1: 1234"
        ));
    }

    @Test
    public void showsNothingWhenNoQueryParamsPresent() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things"),
            aResponse().withBody(
                "{{request.query.multi_param.[0]}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(""));
    }

    @Test
    public void requestHeaders() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .header("X-Request-Id", "req-id-1234")
                .header("123$%$^&__why_o_why", "foundit"),
            aResponse().withBody(
                "Request ID: {{request.headers.X-Request-Id}}, Awkward named header: {{request.headers.[123$%$^&__why_o_why]}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "Request ID: req-id-1234, Awkward named header: foundit"
        ));
    }

    @Test
    public void cookies() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .cookie("session", "session-1234")
                .cookie(")((**#$@#", "foundit"),
            aResponse().withBody(
                "session: {{request.cookies.session}}, Awkward named cookie: {{request.cookies.[)((**#$@#]}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "session: session-1234, Awkward named cookie: foundit"
        ));
    }

    @Test
    public void urlPath() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/the/entire/path"),
            aResponse().withBody(
                "Path: {{request.path}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "Path: /the/entire/path"
        ));
    }

    @Test
    public void urlPathNodes() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/the/entire/path"),
            aResponse().withBody(
                "First: {{request.path.[0]}}, Last: {{request.path.[2]}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "First: the, Last: path"
        ));
    }

    @Test
    public void urlPathNodesForRootPath() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/"),
            aResponse().withBody(
                "{{request.path.[0]}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(""));
    }

    @Test
    public void fullUrl() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/the/entire/path?query1=one&query2=two"),
            aResponse().withBody(
                "URL: {{{request.url}}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "URL: /the/entire/path?query1=one&query2=two"
        ));
    }

    @Test
    public void requestBody() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .body("All of the body content"),
            aResponse().withBody(
                "Body: {{{request.body}}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "Body: All of the body content"
        ));
    }

    @Test
    public void singleValueTemplatedResponseHeaders() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .header("X-Correlation-Id", "12345"),
            aResponse().withHeader("X-Correlation-Id", "{{request.headers.X-Correlation-Id}}")
        );

        assertThat(transformedResponseDef
                .getHeaders()
                .getHeader("X-Correlation-Id")
                .firstValue(),
            is("12345")
        );
    }

    @Test
    public void multiValueTemplatedResponseHeaders() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .header("X-Correlation-Id-1", "12345")
                .header("X-Correlation-Id-2", "56789"),
            aResponse().withHeader("X-Correlation-Id",
                "{{request.headers.X-Correlation-Id-1}}",
                "{{request.headers.X-Correlation-Id-2}}")
        );

        List<String> headerValues = transformedResponseDef
            .getHeaders()
            .getHeader("X-Correlation-Id")
            .values();

        assertThat(headerValues.get(0), is("12345"));
        assertThat(headerValues.get(1), is("56789"));
    }

    @Test
    public void stringHelper() {
        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .body("some text"),
            aResponse().withBody(
                "{{{ capitalize request.body }}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is(
            "Some Text"
        ));
    }

    @Test
    public void customHelper() {
        Helper<String> helper = new Helper<String>() {
            @Override
            public Object apply(String context, Options options) throws IOException {
                return context.length();
            }
        };

        transformer = new ResponseTemplateTransformer(false, "string-length", helper);

        ResponseDefinition transformedResponseDef = transform(mockRequest()
                .url("/things")
                .body("fiver"),
            aResponse().withBody(
                "{{{ string-length request.body }}}"
            )
        );

        assertThat(transformedResponseDef.getBody(), is("5"));
    }

    private ResponseDefinition transform(Request request, ResponseDefinitionBuilder responseDefinitionBuilder) {
        return transformer.transform(
            request,
            responseDefinitionBuilder.build(),
            noFileSource(),
            Parameters.empty()
        );
    }
}
