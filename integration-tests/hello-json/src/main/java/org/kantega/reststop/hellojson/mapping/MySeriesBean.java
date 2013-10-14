package org.kantega.reststop.hellojson.mapping;
/*
 * Copyright 2013 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Test")
public class MySeriesBean {

    private SeriesBean bean;


    public MySeriesBean(SeriesBean bean) {
        this.bean = bean;
    }

    public String getKey() {
        return this.bean.getKey();
    }

    public String getId() {
        return this.bean.getId();
    }

    public void setKey(String key){
        this.bean.setKey(key);
    }

    public void setId(String id){
        this.bean.setKey(id);
    }

    public MySeriesBean() {
    }
}
