(function (root) {
  root.ManoeuvreForm = function(selectedManoeuvreSource) {
    var buttons = '' +
      '<div class="manoeuvres form-controls">' +
        '<button class="save btn btn-primary" disabled>Tallenna</button>' +
        '<button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
    var template = '' +
      '<header>' +
        '<span>Linkin MML ID: <%= mmlId %></span>' +
        buttons +
      '</header>' +
      '<div class="wrapper read-only">' +
        '<div class="form form-horizontal form-dark form-manoeuvre">' +
          '<div></div>' +
        '</div>' +
      '</div>' +
      '<footer>' + buttons + '</footer>';
    var manouvreTemplate = '' +
      '<div class="form-group manoeuvre">' +
        '<label class="control-label">Kääntyminen kielletty linkille </label>' +
        '<p class="form-control-static"><%= destMmlId %></p>' +
        '<% if(localizedExceptions.length > 0) { %>' +
        '<div class="form-group">' +
          '<label>Rajoitus ei koske seuraavia ajoneuvoja</label>' +
          '<ul>' +
            '<% _.forEach(localizedExceptions, function(e) { %> <li><%- e %></li> <% }) %>' +
          '</ul>' +
        '</div>' +
        '<% } %>' +
      '</div>';
    var adjacentLinkTemplate = '' +
      '<div class="form-group adjacent-link" manoeuvreId="<%= manoeuvreId %>" roadLinkId="<%= id %>"  mmlId="<%= mmlId %>" style="display: none">' +
        '<label class="control-label">Kääntyminen kielletty linkille </label>' +
        '<p class="form-control-static"><%= mmlId %></p>' +
        '<div class="checkbox" >' +
          '<input type="checkbox" roadLinkId="<%= id %>"  mmlId="<%= mmlId %>" <% print(checked ? "checked" : "") %>/>' +
        '</div>' +
        '<% _.forEach(localizedExceptions, function(selectedException) { %>' +
        '<select class="form-control exception">' +
          '<% _.forEach(exceptionOptions, function(e, key) { %> ' +
            '<option value="<%- key %>" <% if(selectedException === e) { print(selected="selected")} %> ><%- e %></option> ' +
          '<% }) %>' +
        '</select>' +
        '<% }) %>' +
        '<%= newExceptionSelect %>' +
      '</div>';
    var newExceptionTemplate = '' +
      '<select class="form-control exception new-exception">' +
        '<option class="empty"></option>' +
        '<% _.forEach(exceptionOptions, function(e, key) { %> <option value="<%- key %>"><%- e %></option> <% }) %>' +
      '</select>';

    var exceptions = {
      4: 'Kuorma-auto',
      5: 'Linja-auto',
      6: 'Pakettiauto',
      7: 'Henkilöauto',
      8: 'Taksi',
      13: 'Ajoneuvoyhdistelmä',
      14: 'Traktori tai maatalousajoneuvo',
      15: 'Matkailuajoneuvo',
      16: 'Jakeluauto',
      18: 'Kimppakyytiajoneuvo',
      19: 'Sotilasajoneuvo',
      20: 'Vaarallista lastia kuljettava ajoneuvo',
      21: 'Huoltoajo',
      22: 'Tontille ajo'
    };
    var localizeException = function(e) {
      return exceptions[e];
    };
    var bindEvents = function() {
      var rootElement = $('#feature-attributes');

      function toggleMode(readOnly) {
        rootElement.find('.adjacent-link').toggle(!readOnly);
        rootElement.find('.manoeuvre').toggle(readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
      }
      eventbus.on('application:readOnly', toggleMode);

      eventbus.on('manoeuvres:selected manoeuvres:cancelled', function(roadLink) {
        rootElement.html(_.template(template, roadLink));
        _.each(roadLink.manoeuvres, function(manoeuvre) {
          var attributes = _.merge({}, manoeuvre, {
            localizedExceptions: _.map(manoeuvre.exceptions, function(e) { return localizeException(e); })
          });
          rootElement.find('.form').append(_.template(manouvreTemplate, attributes));
        });
        _.each(roadLink.adjacent, function(adjacentLink) {
          var manoeuvre = _.find(roadLink.manoeuvres, function(manoeuvre) { return adjacentLink.id === manoeuvre.destRoadLinkId; });
          var checked = manoeuvre ? true : false;
          var manoeuvreId = manoeuvre ? manoeuvre.id.toString(10) : "";
          var localizedExceptions = manoeuvre ? _.map(manoeuvre.exceptions, function(e) { return localizeException(e); }) : [];
          var attributes = _.merge({}, adjacentLink, {
            checked: checked,
            manoeuvreId: manoeuvreId,
            exceptionOptions: exceptions,
            localizedExceptions: localizedExceptions,
            newExceptionSelect: _.template(newExceptionTemplate, { exceptionOptions: exceptions })
          });

          rootElement.find('.form').append(_.template(adjacentLinkTemplate, attributes));
        });

        toggleMode(applicationModel.isReadOnly());

        var manoeuvreExceptions = function(formGroupElement) {
          var selectedOptions = formGroupElement.find('select option:selected');
          return _.chain(selectedOptions)
            .map(function(option) { return $(option).val(); })
            .reject(function(value) { return _.isEmpty(value); })
            .map(function(value) { return parseInt(value, 10); })
            .value();
        };

        rootElement.find('.adjacent-link').on('change', 'input', function(event) {
          var eventTarget = $(event.currentTarget);
          var formGroup = $(event.delegateTarget);
          var destRoadLinkId = parseInt(eventTarget.attr('roadLinkId'), 10);
          var destMmlId = parseInt(eventTarget.attr('mmlId'), 10);
          var manoeuvreId = !_.isEmpty(formGroup.attr('manoeuvreId')) ? parseInt(formGroup.attr('manoeuvreId'), 10) : null;
          if (eventTarget.attr('checked') === 'checked') {
            selectedManoeuvreSource.addManoeuvre({
              manoeuvreId: manoeuvreId,
              destRoadLinkId: destRoadLinkId,
              destMmlId: destMmlId,
              exceptions: manoeuvreExceptions(formGroup)
            });
          } else {
            selectedManoeuvreSource.removeManoeuvre({ manoeuvreId: manoeuvreId, destRoadLinkId: destRoadLinkId });
          }
        });
        rootElement.on('change', '.exception', function(event) {
          var selectElement = $(event.target);
          var manoeuvreId = !_.isEmpty(selectElement.parent().attr('manoeuvreId')) ? parseInt(selectElement.parent().attr('manoeuvreId'), 10) : null;
          var destRoadLinkId = parseInt(selectElement.parent().attr('roadLinkId'), 10);
          var destMmlId = parseInt(selectElement.parent().attr('mmlId'), 10);
          var exceptions = manoeuvreExceptions(selectElement.parent());
          console.log(exceptions);
          console.log(selectElement.parent().attr('manoeuvreId'));
          if (_.isNull(manoeuvreId)) {
            selectedManoeuvreSource.addManoeuvre({
              manoeuvreId: manoeuvreId,
              destRoadLinkId: destRoadLinkId,
              destMmlId: destMmlId,
              exceptions: exceptions
            });
          } else {
            selectedManoeuvreSource.setExceptions(manoeuvreId, exceptions);
          }
        });
        rootElement.on('change', '.new-exception', function(event) {
          var selectElement = $(event.target);
          selectElement.parent().append(_.template(newExceptionTemplate, {
            exceptionOptions: exceptions
          }));
          selectElement.removeClass('new-exception');
          selectElement.find('option.empty').remove();
        });
      });
      eventbus.on('manoeuvres:unselected', function() {
        rootElement.empty();
      });
      eventbus.on('manoeuvres:saved', function() {
        rootElement.find('.form-controls button').attr('disabled', true);
      });
      eventbus.on('manoeuvre:changed', function() {
        rootElement.find('.form-controls button').attr('disabled', false);
      });
      rootElement.on('click', '.manoeuvres button.save', function() {
        selectedManoeuvreSource.save();
      });
      rootElement.on('click', '.manoeuvres button.cancel', function() {
        selectedManoeuvreSource.cancel();
      });
    };

    bindEvents();
  };
})(this);
