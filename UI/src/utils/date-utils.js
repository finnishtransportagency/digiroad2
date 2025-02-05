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

  var fromDateString = function (s) {
    return s ? moment(s, dateutil.FINNISH_DATE_FORMAT) : null;
  };

  dateutil.iso8601toFinnish = function (iso8601DateString) {
    if (_.isString(iso8601DateString) && iso8601DateString.trim() !== "") {
      var isAlreadyFinnishFormat = moment(iso8601DateString, FINNISH_DATE_FORMAT, true).isValid();
      if (isAlreadyFinnishFormat) {
        return iso8601DateString;
      } else {
        return moment(iso8601DateString, ISO_8601_DATE_FORMAT).format(FINNISH_DATE_FORMAT);
      }
    }
    return "";
  };

  dateutil.finnishToIso8601 = function (finnishDateString) {
    if (_.isString(finnishDateString) && finnishDateString.trim() !== "") {
      var isAlreadyIso8601Format = moment(finnishDateString, ISO_8601_DATE_FORMAT, true).isValid();
      if (isAlreadyIso8601Format) {
        return finnishDateString;
      } else {
        return moment(finnishDateString, FINNISH_DATE_FORMAT).format(ISO_8601_DATE_FORMAT);
      }
    }
    return "";
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

  dateutil.addDependentDatePickers = function (fromElement, toElement, invElement) {
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
    var invCallback = function () {
      invElement.trigger('datechange');
    };
    datePickers = {
      from: dateutil.addNullableFinnishDatePicker(fromElement, fromCallback),
      to: dateutil.addNullableFinnishDatePicker(toElement, toCallback),
      inv: dateutil.addNullableFinnishDatePicker(invElement, invCallback)
    };
    if (to) {
      datePickers.from.setMaxDate(to);
    }
    if (from) {
      datePickers.to.setMinDate(from);
    }
  };

  dateutil.addTwoDependentDatePickers = function (fromElement, toElement) {
    var startDate = fromDateString(fromElement.val());
    var endDate = fromDateString(toElement.val());
    var datePickers;
    var fromCallback = function () {
      datePickers.endDate.setMinDate(datePickers.startDate.getDate());
      fromElement.trigger('datechange');
    };
    var toCallback = function () {
      datePickers.startDate.setMaxDate(datePickers.endDate.getDate());
      toElement.trigger('datechange');
    };
    datePickers = {
      startDate: dateutil.addNullableFinnishDatePicker(fromElement, fromCallback),
      endDate: dateutil.addNullableFinnishDatePicker(toElement, toCallback)
    };
    if (startDate) {
      datePickers.startDate.setMaxDate(endDate);
    }
    if (endDate) {
      datePickers.endDate.setMinDate(startDate);
    }
  };

  dateutil.addTwoDependentDatePickersForLanes = function (fromElement, toElement) {
    var startDate = fromDateString(fromElement.val());
    var endDate = fromDateString(toElement.val());
    var datePickers;
    var fromCallback = function () {
      datePickers.endDate.setMinDate(datePickers.startDate.getDate());
      fromElement.trigger('datechange');
    };
    var toCallback = function () {
      var selectedEndDate = datePickers.endDate.getDate();
      var dateNow = new Date().setHours(0,0,0,0);
      if(selectedEndDate < dateNow) {
        var endDateAlertPopUpOptions = {
          type: "alert",
          yesButtonLbl: 'Ok',
        };
        var endDateAlertMessage = "Valittu kaistan loppupäivänmäärä on menneisyydessä. Kaista päätetään tallentaessa.";

        GenericConfirmPopup(endDateAlertMessage, endDateAlertPopUpOptions);
      }
      datePickers.startDate.setMaxDate(datePickers.endDate.getDate());
      toElement.trigger('datechange');
    };
    datePickers = {
      startDate: dateutil.addNullableFinnishDatePicker(fromElement, fromCallback),
      endDate: dateutil.addNullableFinnishDatePicker(toElement, toCallback)
    };
    if (startDate) {
      datePickers.startDate.setMaxDate(endDate);
    }
    if (endDate) {
      datePickers.endDate.setMinDate(startDate);
    }
  };

  dateutil.addDependentDatePicker = function (dateElement) {

    var date = fromDateString(dateElement.val());

    var dateCallback = function () {
      dateElement.trigger('datechange');
    };

    var datePickers = {
      date: dateutil.addNullableFinnishDatePicker(dateElement, dateCallback)
    };

    datePickers.date.setDate(date);

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
    var newest = _.maxBy(elementsWithModificationTimestamps, function (s) {
      return moment(s.modifiedAt, "DD.MM.YYYY HH:mm:ss").valueOf() || 0;
    });
    return _.pick(newest, ['modifiedAt', 'modifiedBy']);
  };
}(window.dateutil = window.dateutil || {}));
