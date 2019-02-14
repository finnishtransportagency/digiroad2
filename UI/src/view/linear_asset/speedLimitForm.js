(function (root) {
  var unit = 'km/h';
  var authorizationPolicy = new SpeedLimitAuthorizationPolicy();
  var template = function(selectedSpeedLimit) {
    var modifiedBy = selectedSpeedLimit.getModifiedBy() || '-';
    var modifiedDateTime = selectedSpeedLimit.getModifiedDateTime() ? ' ' + selectedSpeedLimit.getModifiedDateTime() : '';
    var createdBy = selectedSpeedLimit.getCreatedBy() || '-';
    var createdDateTime = selectedSpeedLimit.getCreatedDateTime() ? ' ' + selectedSpeedLimit.getCreatedDateTime() : '';

    var separatorButton = function() {
      if (selectedSpeedLimit.isSeparable()) {
        return '<div class="form-group editable">' +
        '<label class="control-label"></label>' +
        '<button class="cancel btn btn-secondary" id="separate-limit">Jaa nopeusrajoitus kaksisuuntaiseksi</button>' +
        '</div>';
      } else {
        return '';
      }
    };

    var limitValueButtons = function() {
      var singleValueElement = function(sideCode) {
        var SPEED_LIMITS = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
        var defaultUnknownOptionTag = ['<option value="" style="display:none;"></option>'];
        var speedLimitOptionTags = defaultUnknownOptionTag.concat(_.map(SPEED_LIMITS, function(value) {
          var selected = value === selectedSpeedLimit.getValue() ? " selected" : "";
          return '<option value="' + value + '"' + selected + '>' + value + ' ' + unit + '</option>';
        }));
        var speedLimitClass = sideCode ? "speed-limit-" + sideCode : "speed-limit";
        var template =  _.template(
          '<div class="form-group editable">' +
            '<% if(sideCode) { %> <span class="marker"><%- sideCode %></span> <% } %>' +
            '<label class="control-label">Rajoitus</label>' +
            '<p class="form-control-static">' + ((selectedSpeedLimit.getValue() + ' ' + unit) || 'Tuntematon') + '</p>' +
            '<select class="form-control <%- speedLimitClass %>" style="display: none">' + speedLimitOptionTags.join('') + '</select>' +
          '</div>');
        return template({sideCode: sideCode, speedLimitClass: speedLimitClass});
      };
      var separateValueElement = singleValueElement("a") + singleValueElement("b");
      return selectedSpeedLimit.isSplitOrSeparated() ? separateValueElement : singleValueElement();
    };

    var userInformationLog = function() {
      var hasMunicipality = function (linearAsset) {
        return _.some(linearAsset.get(), function (asset) {
          return authorizationPolicy.hasRightsInMunicipality(asset.municipalityCode);
        });
      };

      var limitedRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen. Voit muokata kohteita vain oman kuntasi alueelta.';
      var noRights = 'Käyttöoikeudet eivät riitä kohteen muokkaamiseen.';
      var message = '';

      if (!authorizationPolicy.isOperator() && (authorizationPolicy.isMunicipalityMaintainer() || authorizationPolicy.isElyMaintainer()) && !hasMunicipality(selectedSpeedLimit)) {
        message = limitedRights;
      } else if (!authorizationPolicy.formEditModeAccess(selectedSpeedLimit))
        message = noRights;

      if(message) {
        return '' +
            '<div class="form-group user-information">' +
            '<p class="form-control-static user-log-info">' + message + '</p>' +
            '</div>';
      } else
        return '';
    };


    var informationLog = function (date, username) {
      return date ? (date + ' / ' + username) : ' '+ username;
    };

    return '<div class="wrapper read-only">' +
             '<div class="form form-horizontal form-dark linear-asset">' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n:' + informationLog(createdDateTime, createdBy)+ '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Muokattu viimeksi:' + informationLog(modifiedDateTime, modifiedBy) + '</p>' +
               '</div>' +
               '<div class="form-group">' +
                 '<p class="form-control-static asset-log-info">Linkkien lukumäärä:' + selectedSpeedLimit.count() + '</p>' +
               '</div>' +
               userInformationLog() +
               limitValueButtons() +
               separatorButton() +
             '</div>' +
           '</div>';
  };

  var header = function(selectedSpeedLimit) {
    var title = function() {
      if (selectedSpeedLimit.isUnknown() || selectedSpeedLimit.isSplit()) {
        return '<span>Uusi nopeusrajoitus</span>';
      } else if (selectedSpeedLimit.count() == 1) {
        return '<span>Kohteen ID: ' + selectedSpeedLimit.getId() + '</span>';
      } else {
        return '<span>Nopeusrajoitus</span>';
      }
    };

    return title();
  };

  var footer = function(selectedSpeedLimit) {
    var disabled = selectedSpeedLimit.isDirty() ? '' : 'disabled';
    var buttons = ['<button class="save btn btn-primary" disabled>Tallenna</button>',
      '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>'].join('');

    return '<div class="speed-limit form-controls" style="display: none">' + buttons + '</div>';
  };

  var renderLinktoWorkList = function renderLinktoWorkList() {

      if (!authorizationPolicy.workListAccess()) {
        $('ul[class=information-content]').append('' +
          '<li><button id="work-list-link-errors" class="wrong-speed-limits" onclick=location.href="#work-list/speedLimitErrors/Municipality">Laatuvirhelista</button></li>' +
          '<li><button id="work-list-link" class="unknown-speed-limits" onclick=location.href="#work-list/speedLimit/Municipality">Tuntemattomien nopeusrajoitusten lista</button></li>');
       }
      else {
        $('ul[class=information-content]').append('' +
          '   <li class="log-info"><p class="wrong-speed-limits-log-info"> Laatuvirheet</p></li>' +
          '   <li><button id="work-list-link-municipality" class="wrong-speed-limits-municipality" onclick=location.href="#work-list/speedLimitErrors/Municipality">Kunnan omistama</button></li>' +
          '   <li><button id="work-list-link-state" class="wrong-speed-limits-state" onclick=location.href="#work-list/speedLimitErrors/State">Valtion omistama</button></li>'+

          '   <li class="log-info"><p class="unknown-speed-limits-log-info">Tuntemattomat nopeusrajoitukset</p></li>' +
          '   <li><button id="work-list-link-municipality" class="unknown-speed-limits-municipality" onclick=location.href="#work-list/speedLimit/Municipality">Kunnan omistama</button></li>' +
          '   <li><button id="work-list-link-state" class="unknown-speed-limits-state" onclick=location.href="#work-list/speedLimit/State">Valtion omistama</button></li>');
      }
  };

  var bindEvents = function(selectedSpeedLimit, feedbackCollection) {
    new FeedbackDataTool(feedbackCollection, 'speedLimit', authorizationPolicy);

    var rootElement = $('#feature-attributes');
    var toggleMode = function(readOnly) {
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('#separate-limit').toggle(!readOnly);
      rootElement.find('.editable .form-control').prop('disabled', readOnly);
    };

    eventbus.on('speedLimit:selected speedLimit:cancelled', function() {
      rootElement.find('#feature-attributes-header').html(header(selectedSpeedLimit));
      rootElement.find('#feature-attributes-form').html(template(selectedSpeedLimit));
      rootElement.find('#feature-attributes-footer').html(footer(selectedSpeedLimit));

      var extractValue = function(event) {
        return parseInt($(event.currentTarget).find(':selected').attr('value'), 10);
      };

      rootElement.find('select.speed-limit').change(function(event) {
        selectedSpeedLimit.setValue(extractValue(event));
      });
      rootElement.find('select.speed-limit-a').change(function(event) {
        selectedSpeedLimit.setAValue(extractValue(event));
      });
      rootElement.find('select.speed-limit-b').change(function(event) {
        selectedSpeedLimit.setBValue(extractValue(event));
      });
      rootElement.find('#separate-limit').on('click', function() { selectedSpeedLimit.separate(); });
      rootElement.find('.form-controls.speed-limit button.save').on('click', function() { selectedSpeedLimit.save(); });
      rootElement.find('.form-controls.speed-limit button.cancel').on('click', function() { selectedSpeedLimit.cancel(); });
      toggleMode(validateAdministrativeClass(selectedSpeedLimit) || applicationModel.isReadOnly());
    });
    eventbus.on('speedLimit:unselect', function() {
      rootElement.find('#feature-attributes-header').empty();
      rootElement.find('#feature-attributes-form').empty();
      rootElement.find('#feature-attributes-footer').empty();
    });
    eventbus.on('application:readOnly', function(readOnly){
      toggleMode(validateAdministrativeClass(selectedSpeedLimit) || readOnly);
    });
    eventbus.on('speedLimit:valueChanged', function(selectedSpeedLimit) {
      rootElement.find('.form-controls.speed-limit button.save').attr('disabled', !selectedSpeedLimit.isSaveable());
      rootElement.find('.form-controls.speed-limit button.cancel').attr('disabled', false);
    });
    eventbus.on('layer:selected', function(layer) {
      if(layer === 'speedLimit') {
        $('ul[class=information-content]').empty();
        renderLinktoWorkList();
      }
    });
  };

  function validateAdministrativeClass(selectedSpeedLimit){
    var selectedSpeedLimits = _.filter(selectedSpeedLimit.get(), function (selected) {
      return !authorizationPolicy.formEditModeAccess(selected);
    });
    return !_.isEmpty(selectedSpeedLimits);
  }

  root.SpeedLimitForm = {
    initialize: function(selectedSpeedLimit, feedbackCollection) {
      bindEvents(selectedSpeedLimit, feedbackCollection);
    }
  };
})(this);
