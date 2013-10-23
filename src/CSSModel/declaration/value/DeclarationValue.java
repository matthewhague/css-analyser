package CSSModel.declaration.value;


/**
 * Represents the values of declarations.
 * Note that we keep values such as colors which have a equivalent value
 * in the common representation form (for example, value "black" which
 * is equivalent with "rgba(0, 0, 0, 1)"), using {@link DeclarationEquivalentValue}
 *  
 * @author Davood Mazinanian
 *
 */
public class DeclarationValue implements Cloneable {
	
	protected final String realInFileValue;
	protected boolean isAMissingValue;
	protected final ValueType valueType;
	private final boolean isKeyword;
	
	/**
	 * 
	 * @param realValue The real value of property. Note that the value may not be 
	 * lexically the same as in CSS source file, for instance you may have 
	 * "1px" in real file and you see "1.0px" here.
	 * @param type The type of value based on {@link ValueType}
	 */
	public DeclarationValue(String realValue, ValueType type) {
		this(realValue, false, type);
	}
	
	/**
	 * 
	 * @param value The real value of property. Note that the value may not be 
	 * lexically the same as in CSS source file, for instance you may have 
	 * "1px" in real file and you see "1.0px" here.
	 * @param isMissing If the value is missing and we are adding it later, 
	 * this value would be true.
	 * @param type The type of value based on {@link ValueType}
	 */
	public DeclarationValue(String value, boolean isMissing, ValueType type) {
		this.realInFileValue = value;
		isAMissingValue = isMissing;
		valueType = type;
		// only alphabets or dashes
		isKeyword = realInFileValue.matches("[a-zA-Z\\-]+");
	}
	
	/**
	 * Always returns the real value of 
	 * this declaration value
	 * @return
	 */
	public String getValue() {
		return realInFileValue;
	}

	/**
	 * @return True if current value is missing in 
	 * the real CSS file but we have added it later.
	 * @see #setIsAMissingValue(boolean)
	 */
	public boolean isAMissingValue() {
		return isAMissingValue;
	}
	
	/**
	 * Sets if the value is missing in the real
	 * declaration (and we are adding them when parsing)
	 * <br />
	 * For example, in the declaration <code>margin: 1px</code>
	 * there are three {@link DeclarationValue} objects which are missing
	 * (three <code>1px</code> value for right, bottom and left margins are missing
	 * because <code>margin: 1px</code> is actually <code>margin: 1px 1px 1px 1px</code>)
	 * @param isMissing
	 */
	public void setIsAMissingValue(boolean isMissing) {
		isAMissingValue = isMissing;
	}
	
	/**
	 * Returns true if current value is a keyword.
	 * A non-keyword value is a number, or a number
	 * followed by a unit or percentage symbol.
	 * @return
	 */
	public boolean isKeyword() {
		return isKeyword;
	}
	
	
	/**
	 * Returns the type of this value
	 * @see ValueType
	 * @return Type of this value
	 */
	public ValueType getType() {
		return valueType;
	}
	
	public boolean equivalent(DeclarationValue otherValue) {
		return equals(otherValue);
	}
		
	/**
	 * Hash code is computed based on the realValue
	 */
	@Override
	public int hashCode() {
		return realInFileValue.hashCode();
	}

	/**
	 * Checks the equality based on real value
	 */
	@Override
	public boolean equals(Object obj) { 
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof DeclarationValue))
			return false;
		DeclarationValue other = (DeclarationValue) obj;
		if (realInFileValue == null) {
			if(other.realInFileValue != null)
				return false;
			else
				return true;
		}
		return (realInFileValue.equals(other.realInFileValue));
	}
	
	/**
	 * Returns the real value as a string
	 */
	@Override
	public String toString() {
		return realInFileValue;
	}
	
	/**
	 * Clones the current value, giving a new <code>DeclarationValu</code>
	 * object.
	 */
	@Override
	public DeclarationValue clone() {
		return new DeclarationValue(realInFileValue, isAMissingValue, valueType);
	}
}