/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.data2.transaction.snapshot;

import co.cask.tephra.TransactionManager;
import co.cask.tephra.snapshot.BinaryDecoder;
import co.cask.tephra.snapshot.BinaryEncoder;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Handles serialization/deserialization of a {@link co.cask.tephra.persist.TransactionSnapshot}
 * and its elements to {@code byte[]}.
 *
 * @deprecated Replaced by use of {@code co.cask.tephra.snapshot.SnapshotCodecV3}.
 */
@Deprecated
public class SnapshotCodecV2 extends AbstractSnapshotCodec {
  public static final int VERSION = 2;

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected void encodeInProgress(BinaryEncoder encoder, Map<Long, TransactionManager.InProgressTx> inProgress)
    throws IOException {

    if (!inProgress.isEmpty()) {
      encoder.writeInt(inProgress.size());
      for (Map.Entry<Long, TransactionManager.InProgressTx> entry : inProgress.entrySet()) {
        encoder.writeLong(entry.getKey()); // tx id
        encoder.writeLong(entry.getValue().getExpiration());
        encoder.writeLong(entry.getValue().getVisibilityUpperBound());
      }
    }
    encoder.writeInt(0); // zero denotes end of list as per AVRO spec
  }

  @Override
  protected NavigableMap<Long, TransactionManager.InProgressTx> decodeInProgress(BinaryDecoder decoder)
    throws IOException {

    int size = decoder.readInt();
    NavigableMap<Long, TransactionManager.InProgressTx> inProgress = Maps.newTreeMap();
    while (size != 0) { // zero denotes end of list as per AVRO spec
      for (int remaining = size; remaining > 0; --remaining) {
        long txId = decoder.readLong();
        long expiration = decoder.readLong();
        long visibilityUpperBound = decoder.readLong();
        inProgress.put(txId,
                       new TransactionManager.InProgressTx(visibilityUpperBound, expiration));
      }
      size = decoder.readInt();
    }
    return inProgress;
  }
}
