(function(root) {
  root.ManoeuvreBox = function() {
    var layerName = 'manoeuvre';
    var authorizationPolicy = new AuthorizationPolicy();
    var values = ['Ei kääntymisrajoitusta', 'Kääntymisrajoituksen lähde', 'Kääntymisrajoituksen lähde, useampi', 'Kääntymisrajoituksen välilinkki', 'Kääntymisrajoituksen välilinkki, useampi', 'Kääntymisrajoituksen kohde', 'Kääntymisrajoituksen kohde, useampi', 'Kääntymisrajoituksen lähde ja kohde'];
    var manoeuvreLegendTemplate = _.map(values, function(value, idx) {
      return '<div class="legend-entry">' +
        '<div class="label">' + value + '</div>' +
        '<div class="symbol linear limit-' + idx + '" ></div>' +
        '</div>';
    }).join('');

    var constructionTypeLabeling = '</div>' + [
        '  <div class="panel-section panel-legend linear-asset-legend construction-type-legend">',
        '    <div class="legend-entry">',
        '      <div class="label">Väliaikaisesti poissa käytöstä (haalennettu linkki)</div>',
        '      <div class="symbol linear construction-type-4"></div>',
        '    </div>',
        '  </div>'
      ].join('');

    var manoeuvreSignsCheckBox = [
      '<div class="check-box-container">' +
      '<input id="manoeuvreSignsCheckBox" type="checkbox" /> <lable>Näytä liikennemerkit</lable>' +
      '</div>'
    ].join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Kääntymisrajoitus',
      '  </header>',
      '  <div class="panel-section panel-legend limit-legend">',
      manoeuvreLegendTemplate,
      constructionTypeLabeling,
      manoeuvreSignsCheckBox,
      '  </div>',
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var editModeToggle = new EditModeToggleButton({
      hide: function() {},
      reset: function() {},
      show: function() {}
    });

    var verificationIcon = function() {
      return '<div id="right-panel"><img src="images/check-icon.png" title="Kuntakäyttäjän todentama"/></div>';
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function() {
        if (authorizationPolicy.editModeAccess())
          elements.expanded.append(editModeToggle.element);
      });
      $(elements.expanded).find('.panel-header').css('display', 'flex');
      $(elements.expanded).find('.panel-header').append(verificationIcon());

      eventbus.on('verificationInfo:fetched', function(visible) {
        var img = elements.expanded.find('#right-panel');
        if (visible)
          img.css('display','inline');
        else
          img.css('display','none');
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group manoeuvres-limit manoeuvres"></div>')
      .append(elements.expanded)
      .hide();

    function show() {
      if (!authorizationPolicy.editModeAccess()) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    var template = function() {
      return element;
    };

    $(elements.expanded).find('#manoeuvreSignsCheckBox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger(layerName + '-readOnlyTrafficSigns:show');
      } else {
        eventbus.trigger(layerName + '-readOnlyTrafficSigns:hide');
      }
    });

    return {
      title: 'Kääntymisrajoitus',
      layerName: layerName,
      template: template,
      show: show,
      hide: hide
    };
  };
})(this);

