var tabs = new Object();

function initTabs() {
    var container = document.getElementById('tabs');
    tabs.tabs = findTabs(container);
    tabs.titles = findTitles(tabs.tabs);
    tabs.headers = findHeaders(container);
    tabs.select = select;
    tabs.deselectAll = deselectAll;
    tabs.select(0);
    
    initTestOutput();
    return true;
}

function initTestOutput() {
  var elements = document.getElementsByClassName('collapsibleOutput')
  for (var i = 0; i < elements.length; i++) {
    if (applyToggleText(elements[i].id, true)) {
      elements[i].onclick = toggleOutput;
    }
  }
}

window.onload = initTabs;

function switchTab() {
    var id = this.id.substr(1);
    for (var i = 0; i < tabs.tabs.length; i++) {
        if (tabs.tabs[i].id == id) {
            tabs.select(i);
            break;
        }
    }
    return false;
}

function select(i) {
    this.deselectAll();
    changeElementClass(this.tabs[i], 'tab selected');
    changeElementClass(this.headers[i], 'selected');
    while (this.headers[i].firstChild) {
        this.headers[i].removeChild(this.headers[i].firstChild);
    }
    var h2 = document.createElement('H2');
    h2.appendChild(document.createTextNode(this.titles[i]));
    this.headers[i].appendChild(h2);
}

function deselectAll() {
    for (var i = 0; i < this.tabs.length; i++) {
        changeElementClass(this.tabs[i], 'tab deselected');
        changeElementClass(this.headers[i], 'deselected');
        while (this.headers[i].firstChild) {
            this.headers[i].removeChild(this.headers[i].firstChild);
        }
        var a = document.createElement('A');
        a.setAttribute('id', 'ltab' + i);
        a.setAttribute('href', '#tab' + i);
        a.onclick = switchTab;
        a.appendChild(document.createTextNode(this.titles[i]));
        this.headers[i].appendChild(a);
    }
}

function changeElementClass(element, classValue) {
    if (element.getAttribute('className')) {
        /* IE */
        element.setAttribute('className', classValue)
    } else {
        element.setAttribute('class', classValue)
    }
}

function findTabs(container) {
    return findChildElements(container, 'DIV', 'tab');
}

function findHeaders(container) {
    var owner = findChildElements(container, 'UL', 'tabLinks');
    return findChildElements(owner[0], 'LI', null);
}

function findTitles(tabs) {
    var titles = new Array();
    for (var i = 0; i < tabs.length; i++) {
        var tab = tabs[i];
        var header = findChildElements(tab, 'H2', null)[0];
        header.parentNode.removeChild(header);
        if (header.innerText) {
            titles.push(header.innerText)
        } else {
            titles.push(header.textContent)
        }
    }
    return titles;
}

function findChildElements(container, name, targetClass) {
    var elements = new Array();
    var children = container.childNodes;
    for (var i = 0; i < children.length; i++) {
        var child = children.item(i);
        if (child.nodeType == 1 && child.nodeName == name) {
            if (targetClass && child.className.indexOf(targetClass) < 0) {
                continue;
            }
            elements.push(child);
        }
    }
    return elements;
}

function toggleOutput() {
    var toggleClickId = this.id;
    var outputId = "c" + toggleClickId;
    var text = this.innerHTML;
    var outputElementStyle = document.getElementById(outputId).style;
    outputElementStyle.display = outputElementStyle.display == "none" ? "block" : "none";
    applyToggleText(toggleClickId, false);
}

function applyToggleText(toggleClickId, init) {
    var toggleClickElement = document.getElementById(toggleClickId);
    var output = document.getElementById("c" + toggleClickId);
    var sign = output.style.display == "none" ? "+" : "-";
    if (init) {
        if (output.innerHTML.trim() == "<pre></pre>") {
          toggleClickElement.style.display = "none";
          return false;
        }
        toggleClickElement.innerHTML = "&nbsp;<strong>" + sign + "</strong> " + toggleClickElement.innerHTML + "&nbsp;";
    } else {
        var oldSign = sign == "+" ? "-" : "+";
        toggleClickElement.innerHTML = toggleClickElement.innerHTML.replace(oldSign, sign);
    }
    return true;
}
