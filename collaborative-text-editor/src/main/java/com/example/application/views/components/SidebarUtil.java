package com.example.application.views.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.theme.lumo.LumoUtility.*;
import com.vaadin.flow.component.html.DescriptionList;
import com.vaadin.flow.component.html.DescriptionList.Description;
import com.vaadin.flow.component.html.DescriptionList.Term;

public class SidebarUtil {

    public static Section createSidebar(String viewCode, String editCode, String userId,
                                        Anchor hiddenDownloadLink, Div activeUserListSection) {
        Section sidebar = new Section();
        sidebar.addClassNames(Background.CONTRAST_5, BoxSizing.BORDER, Display.FLEX, FlexDirection.COLUMN,
                Flex.SHRINK_NONE, Overflow.AUTO, Padding.LARGE);
        sidebar.setWidth("256px");

        H2 title = new H2("Project details");
        title.addClassName(Accessibility.SCREEN_READER_ONLY);

        DescriptionList dl = new DescriptionList();
        dl.addClassNames(Display.FLEX, FlexDirection.COLUMN, Gap.LARGE, Margin.Bottom.SMALL, Margin.Top.NONE,
                FontSize.SMALL);

        dl.add(
            createItem("Owner", userId),
            createBadgeItem("View Code", viewCode),
            createBadgeItem("Edit Code", editCode)
        );

        hiddenDownloadLink.setId("hiddenDownloadLink");
        hiddenDownloadLink.getStyle().set("display", "none");
        hiddenDownloadLink.getElement().setAttribute("download", true);

        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.addClickListener(e -> {
            UI.getCurrent().getPage().executeJs(
                "document.getElementById('hiddenDownloadLink').click();"
            );
        });

        sidebar.add(title, exportButton, dl, hiddenDownloadLink, activeUserListSection);
        return sidebar;
    }

    public static Div createBadgeItem(String label, String value) {
        return new Div(createTerm(label), createDescription(value, "badge"));
    }

    public static Div createItem(String label, String value) {
        return new Div(createTerm(label), createDescription(value));
    }

    public static Term createTerm(String label) {
        Term term = new Term(label);
        term.addClassNames(FontWeight.MEDIUM, TextColor.SECONDARY);
        return term;
    }

    public static Description createDescription(String value, String... themeNames) {
        Description desc = new Description(value);
        desc.addClassName(Margin.Left.NONE);
        for (String themeName : themeNames) {
            desc.getElement().getThemeList().add(themeName);
        }
        return desc;
    }

    public static Div createActiveUserListSection() {
        Div container = new Div();
        container.setId("active-users-list");
        container.getStyle()
                .set("margin-top", "1rem")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-body-text-color)")
                .set("font-size", "14px");

        Div header = new Div();
        header.setText("🟢 Active Users:");
        header.getStyle().set("margin-bottom", "0.5rem");
        container.add(header);

        return container;
    }
}