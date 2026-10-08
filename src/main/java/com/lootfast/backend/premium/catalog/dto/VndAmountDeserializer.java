package com.lootfast.backend.premium.catalog.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Prevent Jackson from silently truncating a fractional VND price to a long. */
public class VndAmountDeserializer extends StdDeserializer<Long> {
  public VndAmountDeserializer() {
    super(Long.class);
  }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      return context.reportInputMismatch(Long.class, "priceVnd must be an integer JSON number");
    }
    return parser.getLongValue();
  }
}
