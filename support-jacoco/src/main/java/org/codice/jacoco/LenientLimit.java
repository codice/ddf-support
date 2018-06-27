/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.jacoco;

import java.math.BigDecimal;
import org.jacoco.report.check.Limit;

/**
 * Extension to Jacoco's {@link Limit} class which is more lenient with respect to the minimum and
 * maximum limits to which a value is compared against. This should help account for small differences
 * in Java compiler and OS as these can be generating different bytecodes.
 *
 * <p>System properties supported:
 * <ul>
 *   <li>offsetJacoco - specifies the offset to apply to the minimum and maximum values read from pom
 *   files in order to be lenient about the comparison. Defaults to 0.02.</li>
 *   <li>computeJacoco - forces the limit minimum and maximum values as such that Jacoco's
 *   comparison will fail and yield warnings that includes the new computed values. This can be
 *   useful when one wants to update the pom files after having made changes to the source code or
 *   to the test cases.</li>
 * </ul>
 */
public class LenientLimit extends Limit {
  private static final boolean COMPUTE = System.getProperties().containsKey("computeJacoco");
  private static final BigDecimal OFFSET = new BigDecimal(System.getProperty("offsetJacoco", "0.02"));
  private static final BigDecimal ONE = new BigDecimal("1.00"); // .00 to keep the same scale as jacoco
  private static final BigDecimal ZERO = new BigDecimal("0.00"); // .00 to keep the same scale as jacoco

  @Override
  public void setMinimum(String minimum) {
    if (minimum != null) {
      minimum = (LenientLimit.COMPUTE ? LenientLimit.ONE : new BigDecimal(minimum).subtract(LenientLimit.OFFSET)).toPlainString();
    }
    super.setMinimum(minimum);
  }

  @Override
  public void setMaximum(String maximum) {
    if (maximum != null) {
      maximum = (LenientLimit.COMPUTE ? LenientLimit.ZERO : new BigDecimal(maximum).add(LenientLimit.OFFSET)).toPlainString();
    }
    super.setMaximum(maximum);
  }
}
