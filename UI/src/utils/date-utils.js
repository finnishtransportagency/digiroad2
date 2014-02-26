(function(dateutil, undefined) {
    var FINNISH_DATE_FORMAT = 'D.M.YYYY';
    var ISO_8601_DATE_FORMAT = 'YYYY-MM-DD';
    var FINNISH_HINT_TEXT = 'pp.kk.vvvv';
    var FINNISH_PIKADAY_I18N = {
            previousMonth : 'edellinen kuukausi',
            nextMonth     : 'seuraava kuukausi',
            months: ['Tammikuu','Helmikuu','Maaliskuu','Huhtikuu','Toukokuu','Kesäkuu','Heinäkuu','Elokuu','Syyskuu','Lokakuu','Marraskuu','Joulukuu'],
            weekdays: ['sunnuntai','maanantai','tiistai','keskiviikko','torstai','perjantai','lauantai'],
            weekdaysShort : ['Su','Ma','Ti','Ke','To','Pe','La']
    };

    dateutil.iso8601toFinnish = function(iso8601DateString) {
        return _.isString(iso8601DateString) ? moment(iso8601DateString, ISO_8601_DATE_FORMAT).format(FINNISH_DATE_FORMAT) : "";
    };

    dateutil.finnishToIso8601 = function(finnishDateString) {
        return moment(finnishDateString, FINNISH_DATE_FORMAT).format(ISO_8601_DATE_FORMAT);
    };

    dateutil.addFinnishDatePicker = function(element) {
        return addPicker(jQuery(element));
    };

    dateutil.addNullableFinnishDatePicker = function(element) {
        var elem = jQuery(element);
        var resetButton = jQuery("<div class='pikaday-footer'><div class='deselect-button'>Ei tietoa</div></div>");
        var picker = addPicker(elem, function() {
            jQuery('.pika-single').append(resetButton);
            picker.adjustPosition();
            // FIXME: Dirty hack to prevent odd behavior when clicking year and month selector.
            // Remove once we have a sane feature attribute saving method.
            jQuery('.pika-select').remove();
        });
        resetButton.on('click', function () {
            elem.val(null);
            picker.hide();
            elem.blur();
        });
        return picker;
    };

    dateutil.removeDatePickersFromDom = function() {
        jQuery('.pika-single.is-bound.is-hidden').remove();
    };

    function addPicker(jqueryElement, onDraw) {
        var picker = new Pikaday({
            field: jqueryElement.get(0),
            format: FINNISH_DATE_FORMAT,
            firstDay: 1,
            yearRange: [1950, 2050],
            onDraw: onDraw,
            i18n: FINNISH_PIKADAY_I18N,
        });
        jqueryElement.keypress(function(e){
            if (e.which === 13) { // hide on enter key press
                picker.hide();
                jqueryElement.blur();
            }
        });
        jqueryElement.attr('placeholder', FINNISH_HINT_TEXT);
        return picker;
    }
}(window.dateutil = window.dateutil || {}));