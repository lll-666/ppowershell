/*
 * Copyright 2016-2019 Javier Garcia Alonso.
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
package com.fk.ppowershell;

import com.fk.ppowershell.nonblock.PowerShellNonblocking;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PowerShellConfig {
    private static Properties config;

    public static Properties getConfig() {
        if (config == null) {
            synchronized (PowerShellConfig.class) {
                if (config == null) {
                    config = new Properties();
                    try {
                        config.load(PowerShellConfig.class.getClassLoader().getResourceAsStream("pps.properties"));
                    } catch (IOException e) {
                        Logger.getLogger(PowerShellNonblocking.class.getName()).log(Level.SEVERE, "Cannot read config values from file : pps.properties", e);
                    }
                }
            }
        }
        return config;
    }
}
