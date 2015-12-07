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
          '<div class="form-group">' +
            '<p class="form-control-static asset-log-info">Muokattu viimeksi: <%- modifiedBy %> <%- modifiedAt %> </p>' +
          '</div>' +
          '<label>Kääntyminen kielletty linkeille</label>' +
          '<div></div>' +
        '</div>' +
      '</div>' +
      '<footer>' + buttons + '</footer>';
    var manouvreTemplate = '' +
      '<div class="form-group manoeuvre">' +
        '<p class="form-control-static">MML ID: <%= destMmlId %></p>' +
        '<% if(localizedExceptions.length > 0) { %>' +
        '<div class="form-group">' +
          '<label>Rajoitus ei koske seuraavia ajoneuvoja</label>' +
          '<ul>' +
            '<% _.forEach(localizedExceptions, function(e) { %> <li><%- e.title %></li> <% }) %>' +
          '</ul>' +
        '</div>' +
        '<% } %>' +
        '<% if(!_.isEmpty(additionalInfo)) { %> <label>Tarkenne: <%- additionalInfo %></label> <% } %>' +
      '</div>';
    var adjacentLinkTemplate = '' +
      '<div class="form-group adjacent-link" manoeuvreId="<%= manoeuvreId %>" mmlId="<%= mmlId %>" style="display: none">' +
        '<div class="form-group">' +
          '<div class="checkbox" >' +
            '<input type="checkbox" <% print(checked ? "checked" : "") %>/>' +
          '</div>' +
          '<p class="form-control-static">MML ID <%= mmlId %> <span class="marker"><%= marker %></span></p>' +
        '</div>' +
        '<div class="validity-period-group">' +
        ' <label>Rajoituksen voimassaoloaika (lisäkilvessä):</label>' +
        ' <ul>' +
        '   <%= existingValidityPeriodElements %>' +
        '   <li><div class="form-group new-validity-period">' +
        '     <select class="form-control select">' +
        '       <option class="empty" disabled selected>Lisää voimassaoloaika</option>' +
        '       <option value="Weekday">Ma–Pe</option>' +
        '       <option value="Saturday">La</option>' +
        '       <option value="Sunday">Su</option>' +
        '     </select>' +
        '   </div></li>' +
        ' </ul>' +
        '</div>' +
        '<div class="exception-group <% print(checked ? "" : "exception-hidden") %>">' +
          '<label>Rajoitus ei koske seuraavia ajoneuvoja</label>' +

          '<% _.forEach(localizedExceptions, function(selectedException) { %>' +
            '<div class="form-group exception">' +
              '<%= deleteButtonTemplate %>' +
              '<select class="form-control select">' +
                '<% _.forEach(exceptionOptions, function(exception) { %> ' +
                  '<option value="<%- exception.typeId %>" <% if(selectedException.typeId === exception.typeId) { print(selected="selected")} %> ><%- exception.title %></option> ' +
                '<% }) %>' +
              '</select>' +
            '</div>' +
          '<% }) %>' +
          '<%= newExceptionSelect %>' +
          '<div class="form-group">' +
            '<input type="text" class="form-control additional-info" ' +
                               'placeholder="Muu tarkenne, esim. aika." <% print(checked ? "" : "disabled") %> ' +
                               '<% if(additionalInfo) { %> value="<%- additionalInfo %>" <% } %>/>' +
          '</div>' +
        '<div>' +
      '</div>';
    var newExceptionTemplate = '' +
      '<div class="form-group exception">' +
        '<select class="form-control select new-exception" <% print(checked ? "" : "disabled") %> >' +
          '<option class="empty" disabled selected>Valitse tyyppi</option>' +
          '<% _.forEach(exceptionOptions, function(exception) { %> <option value="<%- exception.typeId %>"><%- exception.title %></option> <% }) %>' +
        '</select>' +
      '</div>';
    var deleteButtonTemplate = '<button class="btn-delete delete">x</button>';

    var bindEvents = function() {
      var rootElement = $('#feature-attributes');

      function toggleMode(readOnly) {
        rootElement.find('.adjacent-link').toggle(!readOnly);
        rootElement.find('.manoeuvre').toggle(readOnly);
        rootElement.find('.form-controls').toggle(!readOnly);
        if(readOnly){
          rootElement.find('.wrapper').addClass('read-only');
        } else {
          rootElement.find('.wrapper').removeClass('read-only');
        }
      }
      eventbus.on('application:readOnly', toggleMode);

      eventbus.on('manoeuvres:selected manoeuvres:cancelled', function(roadLink) {
        roadLink.modifiedBy = roadLink.modifiedBy || '-';
        roadLink.modifiedAt = roadLink.modifiedAt || '';
        rootElement.html(_.template(template)(roadLink));
        _.each(roadLink.manoeuvres, function(manoeuvre) {
          rootElement.find('.form').append(_.template(manouvreTemplate)(_.merge({}, manoeuvre, {
            localizedExceptions: localizeExceptions(manoeuvre.exceptions)
          })));
        });
        _.each(roadLink.adjacent, function(adjacentLink) {
          var manoeuvre = _.find(roadLink.manoeuvres, function(manoeuvre) { return adjacentLink.mmlId === manoeuvre.destMmlId; });
          var checked = manoeuvre ? true : false;
          var manoeuvreId = manoeuvre ? manoeuvre.id.toString(10) : "";
          var localizedExceptions = manoeuvre ? localizeExceptions(manoeuvre.exceptions) : '';
          var additionalInfo = (manoeuvre && !_.isEmpty(manoeuvre.additionalInfo)) ? manoeuvre.additionalInfo : null;
          var existingValidityPeriodElements =
            manoeuvre ?
              _(manoeuvre.validityPeriods)
                .sortByAll(dayOrder, 'startHour', 'endHour')
                .map(validityPeriodElement)
                .join('') :
              '';

          rootElement.find('.form').append(_.template(adjacentLinkTemplate)(_.merge({}, adjacentLink, {
            checked: checked,
            manoeuvreId: manoeuvreId,
            exceptionOptions: exceptionOptions(),
            localizedExceptions: localizedExceptions,
            additionalInfo: additionalInfo,
            newExceptionSelect: _.template(newExceptionTemplate)({ exceptionOptions: exceptionOptions(), checked: checked }),
            deleteButtonTemplate: deleteButtonTemplate,
            existingValidityPeriodElements: existingValidityPeriodElements
          })));
        });

        toggleMode(applicationModel.isReadOnly());

        var manoeuvreData = function(formGroupElement) {
          var destMmlId = parseInt(formGroupElement.attr('mmlId'), 10);
          var manoeuvreId = !_.isEmpty(formGroupElement.attr('manoeuvreId')) ? parseInt(formGroupElement.attr('manoeuvreId'), 10) : null;
          var additionalInfo = !_.isEmpty(formGroupElement.find('.additional-info').val()) ? formGroupElement.find('.additional-info').val() : null;
          return {
            manoeuvreId: manoeuvreId,
            destMmlId: destMmlId,
            exceptions: manoeuvreExceptions(formGroupElement),
            additionalInfo: additionalInfo
          };
        };

        var manoeuvreExceptions = function(formGroupElement) {
          var selectedOptions = formGroupElement.find('select option:selected');
          return _.chain(selectedOptions)
            .map(function(option) { return parseInt($(option).val(), 10); })
            .reject(function(val) { return _.isNaN(val); })
            .value();
        };

        var throttledAdditionalInfoHandler = _.throttle(function(event) {
          var manoeuvre = manoeuvreData($(event.delegateTarget));
          var manoeuvreId = manoeuvre.manoeuvreId;
          if (_.isNull(manoeuvreId)) {
            selectedManoeuvreSource.addManoeuvre(manoeuvre);
          } else {
            selectedManoeuvreSource.setAdditionalInfo(manoeuvreId, manoeuvre.additionalInfo || "");
          }
        }, 1000);
        rootElement.find('.adjacent-link').on('input', 'input[type="text"]', throttledAdditionalInfoHandler);
        rootElement.find('.adjacent-link').on('change', 'input[type="checkbox"]', function(event) {
          var eventTarget = $(event.currentTarget);
          var manoeuvre = manoeuvreData($(event.delegateTarget));
          if (eventTarget.attr('checked') === 'checked') {
            selectedManoeuvreSource.addManoeuvre(manoeuvre);
          } else {
            selectedManoeuvreSource.removeManoeuvre(manoeuvre);
          }
        });
        rootElement.find('.adjacent-link').on('change', '.exception .select', function(event) {
          var manoeuvre = manoeuvreData($(event.delegateTarget));
          var manoeuvreId = manoeuvre.manoeuvreId;
          if (_.isNull(manoeuvreId)) {
            selectedManoeuvreSource.addManoeuvre(manoeuvre);
          } else {
            selectedManoeuvreSource.setExceptions(manoeuvreId, manoeuvre.exceptions);
          }
        });
        rootElement.find('.adjacent-link').on('change', '.new-exception', function(event) {
          var selectElement = $(event.target);
          var formGroupElement = $(event.delegateTarget);
          selectElement.parent().after(_.template(newExceptionTemplate)({
            exceptionOptions: exceptionOptions(),
            checked: true
          }));
          selectElement.removeClass('new-exception');
          selectElement.find('option.empty').remove();
          selectElement.before(deleteButtonTemplate);
          selectElement.parent().on('click', 'button.delete', function(event) {
            deleteException($(event.target).parent(), formGroupElement);
          });
        });
        rootElement.find('.adjacent-link').on('click', '.checkbox :checkbox', function(event) {
          var isChecked = $(event.target).is(':checked');
          var selects = $(event.delegateTarget).find('select');
          var button = $(event.delegateTarget).find('button');
          var text = $(event.delegateTarget).find('input[type="text"]');
          var group = $(event.delegateTarget).find('.exception-group');
          if(isChecked){
            selects.prop('disabled', false);
            button.prop('disabled', false);
            text.prop('disabled', false);
            group.slideDown('fast');
          } else {
            selects.prop('disabled', 'disabled');
            button.prop('disabled', 'disabled');
            text.prop('disabled', 'disabled');
            group.slideUp('fast');
          }
        });
        rootElement.find('.adjacent-link').on('click', '.exception button.delete', function(event) {
          deleteException($(event.target).parent(), $(event.delegateTarget));
        });
        var deleteException = function(exceptionRow, formGroupElement) {
          exceptionRow.remove();
          var manoeuvre = manoeuvreData(formGroupElement);
          if (_.isNull(manoeuvre.manoeuvreId)) {
            selectedManoeuvreSource.addManoeuvre(manoeuvre);
          } else {
            selectedManoeuvreSource.setExceptions(manoeuvre.manoeuvreId, manoeuvre.exceptions);
          }
        };
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

  function validityPeriodElement(period) {
    var dayLabels = {
      Weekday: "Ma–Pe",
      Saturday: "La",
      Sunday: "Su"
    };

    return '' +
      '<li><div class="form-group existing-validity-period" data-days="' + period.days + '">' +
      '  <button class="delete btn-delete">x</button>' +
      '  <label class="control-label">' +
           dayLabels[period.days] +
      '  </label>' +
         hourElement(period.startHour, 'start') +
      '  <span class="hour-separator"> - </span>' +
         hourElement(period.endHour, 'end') +
      '</div></li>';
  }

  function hourElement(selectedHour, type) {
    var className = type + '-hour';
    return '' +
      '<select class="form-control sub-control select ' + className + '">' +
      hourOptions(selectedHour, type) +
      '</select>';
  }

  function hourOptions(selectedOption, type) {
    var range = type === 'start' ? _.range(0, 24) : _.range(1, 25);
    return _.map(range, function (hour) {
      var selected = hour === selectedOption ? 'selected' : '';
      return '<option value="' + hour + '" ' + selected + '>' + hour + '</option>';
    }).join('');
  }

  function localizeExceptions(exceptions) {
    var exceptionTypes = exceptionOptions();

    return _(exceptions)
      .map(function(typeId) {
        return _.find(exceptionTypes, {typeId: typeId});
      })
      .filter()
      .value();
  }

  function exceptionOptions() {
    return [
      {typeId: 21, title: 'Huoltoajo'},
      {typeId: 22, title: 'Tontille ajo'},
      {typeId: 10, title: 'Mopo'},
      {typeId: 9, title: 'Moottoripyörä'},
      {typeId: 27, title: 'Moottorikelkka'},
      {typeId: 5, title: 'Linja-auto'},
      {typeId: 8, title: 'Taksi'},
      {typeId: 7, title: 'Henkilöauto'},
      {typeId: 6, title: 'Pakettiauto'},
      {typeId: 4, title: 'Kuorma-auto'},
      {typeId: 15, title: 'Matkailuajoneuvo'},
      {typeId: 19, title: 'Sotilasajoneuvo'},
      {typeId: 13, title: 'Ajoneuvoyhdistelmä'},
      {typeId: 14, title: 'Traktori tai maatalousajoneuvo'}
    ];
  }

  function dayOrder(period) {
    var days = {
      Weekday: 0,
      Saturday: 1,
      Sunday: 2
    };
    return days[period.days];
  }
})(this);
