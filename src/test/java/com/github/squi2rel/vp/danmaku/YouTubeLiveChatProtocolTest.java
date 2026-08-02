package com.github.squi2rel.vp.danmaku;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeLiveChatProtocolTest {
    @Test
    void selectsInitialRendererContinuationBeforeLiveChatFilter() {
        YouTubeLiveChatProtocol.Bootstrap bootstrap = YouTubeLiveChatProtocol.parseBootstrap(bootstrapPage());

        assertEquals("test-api-key", bootstrap.apiKey());
        assertEquals("1.20260723.00.00", bootstrap.clientVersion());
        assertEquals("initial-continuation", bootstrap.continuation());
        assertEquals("en", bootstrap.contextCopy().getAsJsonObject("client").get("hl").getAsString());
    }

    @Test
    void fallsBackToLiveChatRendererContinuation() {
        String html = """
                <script>
                ytcfg.set({"INNERTUBE_API_KEY":"key","INNERTUBE_CLIENT_VERSION":"version"});
                var ytInitialData = {
                  "conversationBar": {
                    "liveChatRenderer": {
                      "continuations": [
                        {"reloadContinuationData":{"continuation":"fallback-continuation"}}
                      ]
                    }
                  }
                };
                </script>
                """;

        assertEquals("fallback-continuation", YouTubeLiveChatProtocol.parseBootstrap(html).continuation());
    }

    @Test
    void parsesOnlyOrdinaryTextMessages() {
        YouTubeLiveChatProtocol.PollResult result = YouTubeLiveChatProtocol.parsePoll("""
                {
                  "continuationContents": {
                    "liveChatContinuation": {
                      "actions": [
                        {
                          "addChatItemAction": {
                            "item": {
                              "liveChatTextMessageRenderer": {
                                "id": "message-1",
                                "authorName": {"simpleText": "  Alice\nTester  "},
                                "message": {
                                  "runs": [
                                    {"text": "Hello\t"},
                                    {"emoji": {"shortcuts": [":wave:"]}},
                                    {"text": " world"}
                                  ]
                                }
                              }
                            }
                          }
                        },
                        {
                          "addChatItemAction": {
                            "item": {
                              "liveChatPaidMessageRenderer": {
                                "id": "paid-1",
                                "purchaseAmountText": {"simpleText": "$10"}
                              }
                            }
                          }
                        },
                        {
                          "addChatItemAction": {
                            "item": {
                              "liveChatTextMessageRenderer": {
                                "id": "empty-1",
                                "authorName": {"simpleText": ""},
                                "message": {"runs": [{"text": "ignored"}]}
                              }
                            }
                          }
                        }
                      ],
                      "header": {
                        "liveChatHeaderRenderer": {
                          "viewSelector": {
                            "sortFilterSubMenuRenderer": {
                              "subMenuItems": [
                                {
                                  "title": "Top chat",
                                  "selected": true,
                                  "continuation": {
                                    "reloadContinuationData": {
                                      "continuation": "top-filter"
                                    }
                                  }
                                },
                                {
                                  "title": "Live chat",
                                  "selected": false,
                                  "continuation": {
                                    "reloadContinuationData": {
                                      "continuation": "live-filter"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      },
                      "continuations": [
                        {
                          "invalidationContinuationData": {
                            "continuation": "next-continuation",
                            "timeoutMs": 1750
                          }
                        }
                      ]
                    }
                  }
                }
                """);

        assertEquals("next-continuation", result.continuation());
        assertEquals(1750L, result.timeoutMs());
        assertEquals("live-filter", result.liveFilterContinuation());
        assertFalse(result.liveFilterSelected());
        assertEquals(1, result.messages().size());
        assertEquals("message-1", result.messages().getFirst().id());
        assertEquals("Alice Tester: Hello :wave: world", result.messages().getFirst().displayText());
    }

    @Test
    void buildsPollRequestAndClampsPollingDelay() {
        YouTubeLiveChatProtocol.Bootstrap bootstrap = YouTubeLiveChatProtocol.parseBootstrap(bootstrapPage());
        JsonObject body = YouTubeLiveChatProtocol.requestBody(bootstrap, "next");

        assertEquals("next", body.get("continuation").getAsString());
        assertEquals("WEB", body.getAsJsonObject("context").getAsJsonObject("client").get("clientName").getAsString());
        assertEquals(250L, YouTubeLiveChatProtocol.pollingDelay(1L));
        assertEquals(1000L, YouTubeLiveChatProtocol.pollingDelay(0L));
        assertEquals(10_000L, YouTubeLiveChatProtocol.pollingDelay(60_000L));
    }

    @Test
    void rejectsPayloadWithoutContinuationData() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> YouTubeLiveChatProtocol.parseBootstrap("<html></html>"));

        assertTrue(error.getMessage().contains("initial data"));
    }

    private static String bootstrapPage() {
        return """
                <script>
                ytcfg.set({
                  "INNERTUBE_API_KEY": "test-api-key",
                  "INNERTUBE_CLIENT_VERSION": "1.20260723.00.00",
                  "INNERTUBE_CONTEXT": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "1.20260723.00.00",
                      "visitorData": "visitor"
                    }
                  }
                });
                var ytInitialData = {
                  "header": {
                    "liveChatHeaderRenderer": {
                      "viewSelector": {
                        "sortFilterSubMenuRenderer": {
                          "subMenuItems": [
                            {
                              "title": "Top chat",
                              "continuation": {
                                "reloadContinuationData": {
                                  "continuation": "top-continuation"
                                }
                              }
                            },
                            {
                              "title": "Live chat",
                              "continuation": {
                                "reloadContinuationData": {
                                  "continuation": "live-continuation"
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  },
                  "conversationBar": {
                    "liveChatRenderer": {
                      "continuations": [
                        {"reloadContinuationData":{"continuation":"initial-continuation"}}
                      ]
                    }
                  }
                };
                </script>
                """;
    }
}
