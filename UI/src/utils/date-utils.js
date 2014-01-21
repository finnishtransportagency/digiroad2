(function(dateutil, undefined) {
    var FINNISH_DATE_FORMAT = 'D.M.YYYY';
    var ISO_8601_DATE_FORMAT = 'YYYY-MM-DD';
    var FINNISH_PIKADAY_I18N = {
            previousMonth : 'edellinen kuukausi',
            nextMonth     : 'seuraava kuukausi',
            months: ['tammikuu','helmikuu','maaliskuu','huhtikuu','toukokuu','kesäkuu','heinäkuu','elokuu','syyskuu','lokakuu','marraskuu','joulukuu'],
            weekdays: ['sunnuntai','maanantai','tiistai','keskiviikko','torstai','perjantai','lauantai'],
            weekdaysShort : ['su','ma','ti','ke','to','pe','la']
    };

    dateutil.iso8601toFinnish = function(iso8601DateString) {
        return moment(iso8601DateString, ISO_8601_DATE_FORMAT).format(FINNISH_DATE_FORMAT);
    };

    dateutil.finnishToIso8601 = function(finnishDateString) {
        return moment(finnishDateString, FINNISH_DATE_FORMAT).format(ISO_8601_DATE_FORMAT);
    };

    dateutil.addFinnishDatePicker = function(element) {
        return new Pikaday({
            field: element,
            format: FINNISH_DATE_FORMAT,
            firstDay: 1,
            i18n: FINNISH_PIKADAY_I18N
        });
    };
}(window.dateutil = window.dateutil || {}));