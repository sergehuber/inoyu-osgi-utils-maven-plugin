package dev.inoyu.maven.plugins.osgi.analyzer.mojos.converters.blueprint2ds;
/*
 * Copyright 2024 Serge Huber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

public class BlueprintProcessor {

    public Map<String, Object> parseBlueprintFile(File blueprintFile) {
        Map<String, Object> result = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(blueprintFile);

            result.put("propertyPlaceholders", getElements(document, "property-placeholder"));
            result.put("beans", getElements(document, "bean"));
            result.put("services", getElements(document, "service"));
            result.put("references", getElements(document, "reference"));
            result.put("referenceLists", getElements(document, "reference-list"));
            result.put("commands", getElements(document, "command"));

        } catch (Exception e) {
            throw new RuntimeException("Error parsing Blueprint file: " + blueprintFile, e);
        }

        return result;
    }

    private List<Element> getElements(Document document, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = document.getElementsByTagName(tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                elements.add((Element) node);
            }
        }
        return elements;
    }
}
