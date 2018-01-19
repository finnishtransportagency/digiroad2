
var functionToFilter;

/**
 * @classdesc
 *
 * @constructor
 * @extends {ol.Collection}
 * @param {!Array.<T>=} old_collection Array.
 * @param {Function} function_filter Function.
 * @template T
 * @api stable
 */
FilterCollection = function (old_collection, filter_function) {

  functionToFilter = filter_function;

  ol.Collection.call(this);

  /**
   * @private
   * @type {!Array.<T>}
   */
  this.array_ = old_collection.array_ ? old_collection.array_ : [];

};
ol.inherits(FilterCollection, ol.Collection);


/**
 * Iterate over each element, calling the provided callback.
 * @param {function(this: S, T, number, Array.<T>): *} f The function to call
 *     for every element. This function takes 3 arguments (the element, the
 *     index and the array). The return value is ignored.
 * @param {S=} opt_this The object to use as `this` in `f`.
 * @template S
 * @api stable
 */
FilterCollection.prototype.forEach = function(f, opt_this) {
  functionToFilter(this.array_).forEach(f, opt_this);
};

/**
 * Get a reference to the underlying Array object. Warning: if the array
 * is mutated, no events will be dispatched by the collection, and the
 * collection's "length" property won't be in sync with the actual length
 * of the array.
 * @return {!Array.<T>} Array.
 * @api stable
 */
FilterCollection.prototype.getArray = function () {
  return functionToFilter(this.array_);
};

