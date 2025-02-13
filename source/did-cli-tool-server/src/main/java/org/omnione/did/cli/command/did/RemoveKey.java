/*
 * Copyright 2024 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.cli.command.did;

import org.omnione.did.cli.OmniCLI;
import org.omnione.did.cli.command.util.CommandUtils;
import org.omnione.did.core.data.rest.SignatureParams;
import org.omnione.did.core.manager.DidManager;
import org.omnione.did.crypto.enums.DigestType;
import org.omnione.did.crypto.enums.MultiBaseType;
import org.omnione.did.crypto.util.DigestUtils;
import org.omnione.did.crypto.util.MultiBaseUtils;
import org.omnione.did.wallet.key.WalletManagerFactory;
import org.omnione.did.wallet.key.WalletManagerInterface;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "removeKey", mixinStandardHelpOptions = true, version = OmniCLI.VERSION, description = "DID Document remove key")
public class RemoveKey implements Callable<Void> {

    @Option(names = {"-m", "--wallet-manager"}, required = true, description =  "Wallet file path")
    public String walletManager;

    @Option(names = {"-p", "--wallet-manager-password"}, required = true, interactive = true, description = "Wallet password")
    public char[] password;

    @Option(names = {"-f", "--did-file"}, required = true, description = "DID Document save file name")
    public File didFile;

    @Option(names = {"-i", "--key-id"}, required = true, description = "DID Key id")
    public String keyId;

    public DidManager didManager;

    @Override
    public Void call() throws Exception {
        System.out.println("== did remove key call ==");
        CommandUtils.printedCommandMessage(this);

        // 1. connect
        WalletManagerInterface manager = WalletManagerFactory.getWalletManager(WalletManagerFactory.WalletManagerType.FILE);
        manager.connect(walletManager, password);

        // 2. did remove key
        if (manager.isConnect()) {
            File file = didFile;

            if (!file.exists()) {
                System.out.println("Not exists did file...");
                System.out.println("[FAIL] DID remove key fail...");
                return null;
            }

            if (keyId.isEmpty()) {
                System.out.println("No keyId...");
                System.out.println("[FAIL] DID remove key fail...");
                return null;
            }
            keyId = keyId.trim();

            didManager = new DidManager();
            didManager.load(didFile.getPath());

            // remove key
            didManager.removeVerificationMethod(keyId);

            // 4. req signature param
            List<SignatureParams> signatureParams = didManager.getOriginDataForSign(didManager.getAllSignKeyIdList());

            // 5. add proof
            for (SignatureParams params : signatureParams) {
                String keyId = params.getKeyId();
                byte[] hashedSource = DigestUtils.getDigest(params.getOriginData().getBytes(StandardCharsets.UTF_8), DigestType.SHA256);
                byte[] signatureBytes = manager.generateCompactSignatureFromHash(keyId, hashedSource);
                params.setSignatureValue(MultiBaseUtils.encode(signatureBytes, MultiBaseType.base58btc));
            }
            didManager.addProof(signatureParams);
            String dids = didManager.getDocument().toJson();
            CommandUtils.writeFile(didFile, dids);

            System.out.println("dids :\n" + CommandUtils.toPrettyFormat(dids));
            System.out.println("[SUCCESS] DID remove key success...");
        } else {
            System.out.println("[FAIL] DID remove key fail...");
        }
        return null;
    }

}