/*******************************************************************************
 * Copyright 2012 Urbancode, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.urbancode.terraform.tasks.vmware.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import com.urbancode.x2o.util.PropertyResolver;

public class GlobalIpAddressPool {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    final static private Logger log = Logger.getLogger(GlobalIpAddressPool.class);
    private static GlobalIpAddressPool instance = new GlobalIpAddressPool();

    //----------------------------------------------------------------------------------------------
    public static GlobalIpAddressPool getInstance() {
        return instance;
    }

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    private IpAddressPool addressPool = null;
    private PropertyResolver resolver = null;

    //----------------------------------------------------------------------------------------------
    private GlobalIpAddressPool() {
        Properties poolConfig = parseIpPoolFile();
        addressPool = createIpPoolFromProps(poolConfig);
    }

    //----------------------------------------------------------------------------------------------
    public void createIpPoolFromUserProps() {
        if(resolver != null) {
            String start = resolver.resolve("${ip.pool.start}");
            String end = resolver.resolve("${ip.pool.end}");
            if (!(start == null || "".equals(start) || "null".equalsIgnoreCase(start) ||
                    end == null || "".equals(end) || "null".equalsIgnoreCase(end))) {
                addressPool = new IpAddressPool(start, end);
                log.info("Reformatted IP address pool with start: " + start + " and end: " + end);
            }
            else {
                log.debug("IP pool properties were not found; IP pool is unchanged.");
            }
        }
        else {
            log.info("The global IP pool property resolver was not set properly.");
        }
    }

    //----------------------------------------------------------------------------------------------
    public void setPropertyResolver(PropertyResolver resolver) {
        this.resolver = resolver;
    }

    //----------------------------------------------------------------------------------------------
    private IpAddressPool createIpPoolFromProps(Properties props) {

        if (props == null) {
            // fallback to defaults if we have null props
            props = new Properties();
        }

        String start = props.getProperty("start", "10.15.50.1");
        log.info("IpAddressPool start: " + start);

        String end = props.getProperty("end", "10.15.50.250");
        log.info("IpAddressPool end: " + end);

        return new IpAddressPool(start, end);
    }

    //----------------------------------------------------------------------------------------------
    private Properties parseIpPoolFile() {
        Properties result = new Properties();
        InputStream in = null;
        String inputFname = System.getenv("TERRAFORM_HOME") + File.separator + "conf" + File.separator + "ippool.conf";
        try {
            in = FileUtils.openInputStream(new File(inputFname));
            result.load(in);
        }
        catch (IOException e) {
            log.error("Could not read properties from input stream", e);
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    // swallow
                }
            }
        }

        return result;
    }

    //----------------------------------------------------------------------------------------------
    public IpAddressPool getIpAddressPool() {
        return addressPool;
    }

    //----------------------------------------------------------------------------------------------
    synchronized public void reserveIp(Ip4 ip)
    throws IpInUseException {
        addressPool.reserveIp(ip);
    }

    //----------------------------------------------------------------------------------------------
    synchronized public Ip4 allocateIp() {
        Ip4 result = addressPool.allocateIp();
        return result;
    }

    //----------------------------------------------------------------------------------------------
    synchronized public void releaseIp(Ip4 ip) {
        addressPool.releaseIp(ip);
    }
}
