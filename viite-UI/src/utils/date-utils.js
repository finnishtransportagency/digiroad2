(function (dateutil, undefined) {
  var FINNISH_DATE_FORMAT = 'D.M.YYYY';
  dateutil.FINNISH_DATE_FORMAT = FINNISH_DATE_FORMAT;
  var ISO_8601_DATE_FORMAT = 'YYYY-MM-DD';
  var FINNISH_HINT_TEXT = 'pp.kk.vvvv';
  var FINNISH_PIKADAY_I18N = {
    previousMonth: 'edellinen kuukausi',
    nextMonth: 'seuraava kuukausi',
    months: ['Tammikuu', 'Helmikuu', 'Maaliskuu', 'Huhtikuu', 'Toukokuu', 'Kes&auml;kuu', 'Hein&auml;kuu', 'Elokuu', 'Syyskuu', 'Lokakuu', 'Marraskuu', 'Joulukuu'],
    weekdays: ['sunnuntai', 'maanantai', 'tiistai', 'keskiviikko', 'torstai', 'perjantai', 'lauantai'],
    weekdaysShort: ['Su', 'Ma', 'Ti', 'Ke', 'To', 'Pe', 'La']
  };

  dateutil.iso8601toFinnish = function (iso8601DateString) {
    return _.isString(iso8601DateString) ? moment(iso8601DateString, ISO_8601_DATE_FORMAT).format(FINNISH_DATE_FORMAT) : "";
  };

  dateutil.finnishToIso8601 = function (finnishDateString) {
    return moment(finnishDateString, FINNISH_DATE_FORMAT).format(ISO_8601_DATE_FORMAT);
  };

  dateutil.addFinnishDatePicker = function (element) {
    return addPicker(jQuery(element));
  };

  dateutil.addNullableFinnishDatePicker = function (element, onSelect) {
    var elem = jQuery(element);
    var resetButton = jQuery("<div class='pikaday-footer'><div class='deselect-button'>Ei tietoa</div></div>");
    var picker = addPicker(elem, function () {
      jQuery('.pika-single').append(resetButton);
      picker.adjustPosition();
      // FIXME: Dirty hack to prevent odd behavior when clicking year and month selector.
      // Remove once we have a sane feature attribute saving method.
      jQuery('.pika-select').remove();
    }, onSelect);
    resetButton.on('click', function () {
      elem.val(null);
      elem.trigger('datechange');
      picker.hide();
      elem.blur();
    });
    return picker;
  };

  dateutil.addDependentDatePickers = function (fromElement, toElement) {
    var fromDateString = function (s) {
      return s ? moment(s, dateutil.FINNISH_DATE_FORMAT) : null;
    };
    var from = fromDateString(fromElement.val());
    var to = fromDateString(toElement.val());
    var datePickers;
    var fromCallback = function () {
      datePickers.to.setMinDate(datePickers.from.getDate());
      fromElement.trigger('datechange');
    };
    var toCallback = function () {
      datePickers.from.setMaxDate(datePickers.to.getDate());
      toElement.trigger('datechange');
    };
    datePickers = {
      from: dateutil.addNullableFinnishDatePicker(fromElement, fromCallback),
      to: dateutil.addNullableFinnishDatePicker(toElement, toCallback)
    };
    if (to) {
      datePickers.from.setMaxDate(to);
    }
    if (from) {
      datePickers.to.setMinDate(from);
    }
  };

  dateutil.addSingleDependentDatePicker = function (fromElement) {
    var fromDateString = function (s) {
      return s ? moment(s, dateutil.FINNISH_DATE_FORMAT) : null;
    };
    var from = fromDateString(fromElement.val());
    var datePicker;
    datePicker = {
      from: dateutil.addFinnishDatePicker(fromElement)
    };
  };

  dateutil.removeDatePickersFromDom = function () {
    jQuery('.pika-single.is-bound.is-hidden').remove();
  };

  function addPicker(jqueryElement, onDraw, onSelect) {
    var picker = new Pikaday({
      field: jqueryElement.get(0),
      format: FINNISH_DATE_FORMAT,
      firstDay: 1,
      yearRange: [1950, 2050],
      onDraw: onDraw,
      onSelect: onSelect,
      i18n: FINNISH_PIKADAY_I18N
    });
    jqueryElement.keypress(function (e) {
      if (e.which === 13) { // hide on enter key press
        picker.hide();
        jqueryElement.blur();
      }
    });
    jqueryElement.attr('placeholder', FINNISH_HINT_TEXT);
    return picker;
  }

  dateutil.extractLatestModifications = function (elementsWithModificationTimestamps) {
    var newest = _.max(elementsWithModificationTimestamps, function (s) {
      return moment(s.modifiedAt, "DD.MM.YYYY HH:mm:ss").valueOf() || 0;
    });
    return _.pick(newest, ['modifiedAt', 'modifiedBy']);
  };
}(window.dateutil = window.dateutil || {}));
