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
package com.urbancode.terraform.tasks.vmware.events;

import java.util.EventObject;

import com.urbancode.x2o.tasks.SubTask;



public class TaskEvent extends EventObject {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************

    static private final long serialVersionUID = 1L;

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    private SubTask subTask;

    //----------------------------------------------------------------------------------------------
    public TaskEvent(SubTask subTask) {
        super(subTask);
        this.subTask = subTask;
    }

    //----------------------------------------------------------------------------------------------
    public SubTask getSubTask() {
        return this.subTask;
    }

}
