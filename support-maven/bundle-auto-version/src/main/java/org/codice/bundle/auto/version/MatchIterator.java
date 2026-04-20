/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General private License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General private License for more details. A copy of the GNU Lesser General private
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.bundle.auto.version;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.regex.Matcher;

class MatchIterator extends Spliterators.AbstractSpliterator<String> {
  private final Matcher matcher;

  MatchIterator(Matcher m) {
    super(m.regionEnd() - m.regionStart(), ORDERED | NONNULL);
    matcher = m;
  }

  @Override
  public boolean tryAdvance(Consumer<? super String> action) {
    if (!matcher.find()) return false;
    action.accept(matcher.group());
    return true;
  }
}
